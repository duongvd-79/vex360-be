package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Mock private JwtService jwtService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService(
                jwtService,
                refreshTokenRepository,
                accessTokenRevocationService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3_600_000L);
    }

    @Test
    void issue_ReplacesExistingSession() {
        User user = User.builder().email("user@example.com").build();
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("access-token");

        var response = service.issue(user);

        assertEquals("access-token", response.getAccessToken());
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_ConsumesTokenAndIssuesReplacement() {
        User user = User.builder().email("user@example.com").build();
        RefreshToken current = RefreshToken.builder()
                .token("refresh-token")
                .expiryDate(NOW.plusSeconds(60))
                .used(false)
                .user(user)
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("refresh-token")).thenReturn(Optional.of(current));
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("new-access-token");

        var response = service.rotate("refresh-token");

        assertEquals("new-access-token", response.getAccessToken());
        assertTrue(current.isUsed());
        verify(refreshTokenRepository).save(current);
    }

    @Test
    void rotate_ExpiredTokenIsDeletedAndRejected() {
        RefreshToken expired = RefreshToken.builder()
                .token("expired-token")
                .expiryDate(NOW.minusSeconds(1))
                .used(false)
                .user(User.builder().email("user@example.com").build())
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("expired-token")).thenReturn(Optional.of(expired));

        AppException exception = assertThrows(AppException.class, () -> service.rotate("expired-token"));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void rotate_ReplayedTokenRevokesAllSessions() {
        User user = User.builder().email("user@example.com").build();
        RefreshToken replayed = RefreshToken.builder()
                .token("used-token")
                .expiryDate(NOW.plusSeconds(60))
                .used(true)
                .user(user)
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("used-token")).thenReturn(Optional.of(replayed));

        AppException exception = assertThrows(AppException.class, () -> service.rotate("used-token"));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void logout_UsesNonLockingLookupAndBlacklistsAccessToken() {
        RefreshToken refreshToken = RefreshToken.builder().token("refresh-token").build();
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

        service.logout("refresh-token", "access-token");

        verify(refreshTokenRepository).delete(refreshToken);
        verify(accessTokenRevocationService).blacklist("access-token");
    }
}
