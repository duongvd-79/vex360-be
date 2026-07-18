package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private MailService mailService;
    @Mock
    private AuthSessionService authSessionService;

    private PasswordService service;

    @BeforeEach
    void setUp() {
        service = new PasswordService(
                userService,
                passwordEncoder,
                resetTokenRepository,
                mailService,
                authSessionService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "http://localhost:8080");
        TokenEncryptionUtils.setKey("TestSecretKey1234");
    }

    @Test
    void forgotPassword_UnknownEmailDoesNotRevealAccount() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.empty());

        service.forgotPassword(request);

        verify(resetTokenRepository, never()).save(any());
        verify(mailService, never()).sendForgotPasswordEmail(any(), any());
    }

    @Test
    void resetPassword_UpdatesPasswordAndRevokesSessions() {
        User user = User.builder().id(UUID.randomUUID()).build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("raw-token")
                .expiryDate(NOW.plusSeconds(60))
                .user(user)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(TokenEncryptionUtils.encrypt("raw-token"));
        request.setNewPassword("new-password");
        when(resetTokenRepository.findByToken("raw-token")).thenReturn(Optional.of(token));

        service.resetPassword(request, "access-token");

        verify(userService).updatePassword(user, "new-password");
        verify(resetTokenRepository).delete(token);
        verify(authSessionService).revokeAll(user, "access-token");
    }

    @Test
    void changePassword_ReloadsPersistedUserBeforeCheckingHash() {
        UUID userId = UUID.randomUUID();
        User persistedUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("persisted-hash")
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest("old-password", "new-password");
        when(userService.getUserEntityById(userId)).thenReturn(persistedUser);
        when(passwordEncoder.matches("old-password", "persisted-hash")).thenReturn(true);

        service.changePassword(userId, request, "access-token");

        verify(userService).updatePassword(persistedUser, "new-password");
        verify(authSessionService).revokeAll(persistedUser, "access-token");
    }

    @Test
    void changePassword_WrongOldPasswordDoesNotMutateAccount() {
        UUID userId = UUID.randomUUID();
        User persistedUser = User.builder().id(userId).password("persisted-hash").build();
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "new-password");
        when(userService.getUserEntityById(userId)).thenReturn(persistedUser);
        when(passwordEncoder.matches("wrong", "persisted-hash")).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.changePassword(userId, request, null));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(userService, never()).updatePassword(any(), any());
        verify(authSessionService, never()).revokeAll(any(), any());
    }
}
