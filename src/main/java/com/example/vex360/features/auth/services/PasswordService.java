package com.example.vex360.features.auth.services;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.LogSanitizer;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles password recovery and authenticated password changes. Successful
 * password updates revoke active sessions to prevent continued use of old
 * credentials.
 */
@Service
@Slf4j
public class PasswordService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final MailService mailService;
    private final AuthSessionService authSessionService;
    private final Clock clock;
    private final String backendBaseUrl;

    public PasswordService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository resetTokenRepository,
            MailService mailService,
            AuthSessionService authSessionService,
            Clock clock,
            @Value("${app.backend.base-url}") String backendBaseUrl) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenRepository = resetTokenRepository;
        this.mailService = mailService;
        this.authSessionService = authSessionService;
        this.clock = clock;
        this.backendBaseUrl = backendBaseUrl;
    }

    /**
     * Creates and emails a one-hour password-reset token when the account exists.
     * Unknown emails complete silently to prevent account enumeration.
     *
     * @param request email requesting password recovery
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> user = userService.findUserByEmail(request.getEmail());
        if (user.isEmpty()) {
            log.info("Forgot password requested for non-existent email (Anti-Enumeration active): {}",
                    LogSanitizer.sanitize(request.getEmail()));
            return;
        }

        User persistedUser = user.get();
        if (persistedUser.getProvider() != AuthProvider.LOCAL) {
            throw new AppException(ErrorCode.PROVIDER_NOT_SUPPORT_FORGOT_PASSWORD);
        }

        resetTokenRepository.deleteByUser(persistedUser);
        String tokenValue = UUID.randomUUID().toString();
        resetTokenRepository.save(PasswordResetToken.builder()
                .token(tokenValue)
                .expiryDate(Instant.now(clock).plus(1, ChronoUnit.HOURS))
                .user(persistedUser)
                .build());

        String encryptedToken = TokenEncryptionUtils.encrypt(tokenValue);
        String resetUrl = backendBaseUrl + "/api/v1/auth/reset-password/validate?token=" + encryptedToken;
        mailService.sendForgotPasswordEmail(persistedUser.getEmail(), resetUrl);
    }

    /**
     * Verifies that an encrypted password-reset token exists and has not expired.
     *
     * @param encryptedToken encrypted reset token from the email link
     * @throws AppException when the token is missing or expired
     */
    @Transactional(readOnly = true)
    public void validateResetToken(String encryptedToken) {
        PasswordResetToken resetToken = getResetToken(encryptedToken);
        if (resetToken.getExpiryDate().isBefore(Instant.now(clock))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /**
     * Replaces the user's password using a valid reset token, consumes that token,
     * and revokes all active sessions.
     *
     * @param request     reset token and new password
     * @param accessToken current access token, or {@code null} when unavailable
     * @throws AppException when the reset token is missing or expired
     */
    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(ResetPasswordRequest request, String accessToken) {
        PasswordResetToken resetToken = getResetToken(request.getToken());
        if (resetToken.getExpiryDate().isBefore(Instant.now(clock))) {
            resetTokenRepository.delete(resetToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = resetToken.getUser();
        userService.updatePassword(user, request.getNewPassword());
        resetTokenRepository.delete(resetToken);
        authSessionService.revokeAll(user, accessToken);
    }

    /**
     * Changes the authenticated user's password after verifying the old password,
     * then revokes all sessions and sends a security notification.
     *
     * @param currentUserId authenticated user's identifier
     * @param request       old and replacement passwords
     * @param accessToken   current access token, or {@code null} when unavailable
     * @throws AppException when the old password does not match
     */
    @Transactional
    public void changePassword(UUID currentUserId, ChangePasswordRequest request, String accessToken) {
        User user = userService.getUserEntityById(currentUserId);
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new AppException(ErrorCode.PROVIDER_NOT_SUPPORT_CHANGE_PASSWORD);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLDPASSWORD_FAILED);
        }

        userService.updatePassword(user, request.getNewPassword());
        resetTokenRepository.deleteByUser(user);
        authSessionService.revokeAll(user, accessToken);
        mailService.sendPasswordChangeNotificationEmail(user.getEmail());
    }

    private PasswordResetToken getResetToken(String encryptedToken) {
        String rawToken = TokenEncryptionUtils.decrypt(encryptedToken);
        return resetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
