package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.services.AuthSessionService;
import com.example.vex360.features.auth.services.PasswordService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.Role;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

@ExtendWith(MockitoExtension.class)
class PasswordServiceUnitTest {

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

    private Clock clock;
    private PasswordService passwordService;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneId.of("UTC"));
        passwordService = new PasswordService(
                userService,
                passwordEncoder,
                resetTokenRepository,
                mailService,
                authSessionService,
                clock,
                "http://localhost:8080"
        );

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded_old_pass")
                .provider(AuthProvider.LOCAL)
                .role(Role.VISITOR)
                .build();
    }

    @Test
    void forgotPassword_UserNotFound_LogsAndReturnsSilently() {
        when(userService.findUserByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        passwordService.forgotPassword(new ForgotPasswordRequest("nonexistent@example.com"));

        verify(resetTokenRepository, never()).save(any());
        verify(mailService, never()).sendForgotPasswordEmail(any(), any());
    }

    @Test
    void forgotPassword_UserFound_SavesResetTokenAndSendsEmail() {
        when(userService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        passwordService.forgotPassword(new ForgotPasswordRequest("user@example.com"));

        verify(resetTokenRepository).deleteByUser(user);
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendForgotPasswordEmail(eq("user@example.com"), contains("token="));
    }

    @Test
    void validateResetToken_ValidToken_Success() {
        String rawToken = "raw_reset_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-01T11:00:00Z")) // Valid for 1 more hr
                .build();
        when(resetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        passwordService.validateResetToken(encryptedToken);
    }

    @Test
    void validateResetToken_ExpiredToken_ThrowsAppException() {
        String rawToken = "raw_expired_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-01T09:00:00Z")) // Expired 1 hr ago
                .build();
        when(resetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        AppException ex = assertThrows(AppException.class, () -> passwordService.validateResetToken(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void validateResetToken_TokenNotFound_ThrowsAppException() {
        String rawToken = "unknown_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        when(resetTokenRepository.findByToken(rawToken)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> passwordService.validateResetToken(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void resetPassword_ValidToken_UpdatesPasswordAndRevokesSessions() {
        String rawToken = "raw_reset_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-01T11:00:00Z"))
                .build();
        when(resetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        ResetPasswordRequest request = new ResetPasswordRequest(encryptedToken, "NewPassword123!");
        passwordService.resetPassword(request, "acc_token");

        verify(userService).updatePassword(user, "NewPassword123!");
        verify(resetTokenRepository).delete(resetToken);
        verify(authSessionService).revokeAll(user, "acc_token");
    }

    @Test
    void resetPassword_ExpiredToken_DeletesTokenAndThrowsAppException() {
        String rawToken = "raw_expired_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-01T09:00:00Z"))
                .build();
        when(resetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        ResetPasswordRequest request = new ResetPasswordRequest(encryptedToken, "NewPassword123!");

        AppException ex = assertThrows(AppException.class, () -> passwordService.resetPassword(request, "acc_token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());

        verify(resetTokenRepository).delete(resetToken);
    }

    @Test
    void changePassword_NonLocalProvider_ThrowsAppException() {
        user.setProvider(AuthProvider.GOOGLE);
        when(userService.getUserEntityById(user.getId())).thenReturn(user);

        ChangePasswordRequest request = new ChangePasswordRequest("old_pass", "new_pass");

        AppException ex = assertThrows(AppException.class, () -> passwordService.changePassword(user.getId(), request, "acc_token"));
        assertEquals(ErrorCode.PROVIDER_NOT_SUPPORT_CHANGE_PASSWORD, ex.getErrorCode());
    }

    @Test
    void changePassword_OldPasswordMismatch_ThrowsAppException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(passwordEncoder.matches("wrong_old_pass", "encoded_old_pass")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("wrong_old_pass", "new_pass");

        AppException ex = assertThrows(AppException.class, () -> passwordService.changePassword(user.getId(), request, "acc_token"));
        assertEquals(ErrorCode.OLDPASSWORD_FAILED, ex.getErrorCode());
    }

    @Test
    void changePassword_Success() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(passwordEncoder.matches("old_pass", "encoded_old_pass")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("old_pass", "new_pass");
        passwordService.changePassword(user.getId(), request, "acc_token");

        verify(userService).updatePassword(user, "new_pass");
        verify(resetTokenRepository).deleteByUser(user);
        verify(authSessionService).revokeAll(user, "acc_token");
        verify(mailService).sendPasswordChangeNotificationEmail("user@example.com");
    }
}
