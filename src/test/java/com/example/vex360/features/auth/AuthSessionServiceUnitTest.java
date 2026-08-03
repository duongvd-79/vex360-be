package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.services.AccessTokenRevocationService;
import com.example.vex360.features.auth.services.AuthSessionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.enums.Role;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceUnitTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;

    private Clock clock;
    private AuthSessionService authSessionService;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneId.of("UTC"));
        authSessionService = new AuthSessionService(
                jwtService,
                refreshTokenRepository,
                accessTokenRevocationService,
                clock,
                86400000L, // 1 day
                2592000000L // 30 days
        );

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.VISITOR)
                .build();
    }

    @Test
    void issue_NormalAndRememberMe_DeletesOldAndSavesNewToken() {
        when(jwtService.generateToken(any())).thenReturn("jwt_access_token");

        TokenResponse response = authSessionService.issue(user, true);

        assertNotNull(response);
        assertEquals("jwt_access_token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(response.isRememberMe());

        verify(refreshTokenRepository).deleteByUser(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertTrue(captor.getValue().isRememberMe());
        assertEquals(user, captor.getValue().getUser());
    }

    @Test
    void rotate_TokenNotFound_ThrowsAppException() {
        when(refreshTokenRepository.findByTokenForUpdate("invalid")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authSessionService.rotate("invalid"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void rotate_TokenAlreadyUsed_SecurityAlertAndRevokesAllSessions() {
        RefreshToken token = RefreshToken.builder()
                .token("used_token")
                .user(user)
                .used(true)
                .expiryDate(Instant.parse("2026-08-02T10:00:00Z"))
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("used_token")).thenReturn(Optional.of(token));

        AppException ex = assertThrows(AppException.class, () -> authSessionService.rotate("used_token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());

        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void rotate_TokenExpired_DeletesTokenAndThrowsAppException() {
        RefreshToken token = RefreshToken.builder()
                .token("expired_token")
                .user(user)
                .used(false)
                .expiryDate(Instant.parse("2026-08-01T09:00:00Z")) // Expired 1 hr ago
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("expired_token")).thenReturn(Optional.of(token));

        AppException ex = assertThrows(AppException.class, () -> authSessionService.rotate("expired_token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void rotate_Success_MarksCurrentUsedAndSavesNewToken() {
        RefreshToken token = RefreshToken.builder()
                .token("valid_token")
                .user(user)
                .used(false)
                .rememberMe(false)
                .expiryDate(Instant.parse("2026-08-02T10:00:00Z"))
                .build();
        when(refreshTokenRepository.findByTokenForUpdate("valid_token")).thenReturn(Optional.of(token));
        when(jwtService.generateToken(any())).thenReturn("new_access_token");

        TokenResponse response = authSessionService.rotate("valid_token");

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertTrue(token.isUsed());

        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_WithRefreshTokenAndAccessToken() {
        RefreshToken token = RefreshToken.builder().token("ref_token").user(user).build();
        when(refreshTokenRepository.findByToken("ref_token")).thenReturn(Optional.of(token));

        authSessionService.logout("ref_token", "acc_token");

        verify(refreshTokenRepository).delete(token);
        verify(accessTokenRevocationService).blacklist("acc_token");
    }

    @Test
    void logout_WithBlankRefreshToken() {
        authSessionService.logout("", "acc_token");

        verify(refreshTokenRepository, never()).findByToken(any());
        verify(accessTokenRevocationService).blacklist("acc_token");
    }

    @Test
    void revokeAll_DeletesUserTokensAndBlacklistsAccessToken() {
        authSessionService.revokeAll(user, "acc_token");

        verify(refreshTokenRepository).deleteByUser(user);
        verify(accessTokenRevocationService).blacklist("acc_token");
    }
}
