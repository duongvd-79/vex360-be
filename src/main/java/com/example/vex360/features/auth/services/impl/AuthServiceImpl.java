package com.example.vex360.features.auth.services.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.auth.dtos.*;
import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.mapper.AuthMapper;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.UserService;
import com.example.vex360.features.user.dtos.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.UserRequestDTO;
import com.example.vex360.shared.config.security.JwtProvider;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        UserRequestDTO userRequest = authMapper.toUserRequestDTO(request);
        userService.createUser(userRequest);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userService.getUserByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (!user.getIsActive()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Generate Access Token
        String accessToken = jwtProvider.generateToken(user.getEmail(), user.getRole().name());

        // Generate and Save Refresh Token
        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .user(user)
                .build();
        
        // Remove existing refresh tokens for clean state
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(tokenStr)
                .build();
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(String tokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtProvider.generateToken(user.getEmail(), user.getRole().name());

        // Rotate Refresh Token
        String newRefreshTokenStr = UUID.randomUUID().toString();
        refreshToken.setToken(newRefreshTokenStr);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .build();
    }

    @Override
    @Transactional
    public void logout(String tokenStr) {
        refreshTokenRepository.findByToken(tokenStr).ifPresent(refreshTokenRepository::delete);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Find user. Prevent email enumeration by returning silently if not found.
        try {
            User user = userService.getUserByEmail(request.getEmail());

            // Clear any existing reset tokens
            passwordResetTokenRepository.deleteByUser(user);

            // Generate Reset Token
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .expiryDate(Instant.now().plusSeconds(3600)) // 1 hour expiration
                    .user(user)
                    .build();

            passwordResetTokenRepository.save(resetToken);

            // Send reset email
            mailService.sendForgotPasswordEmail(user.getEmail(), token);
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                log.info("Forgot password requested for non-existent email: {}", request.getEmail());
            } else {
                throw e;
            }
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = resetToken.getUser();
        userService.updatePassword(user, request.getNewPassword());

        // Invalidate reset token and refresh tokens on password change
        passwordResetTokenRepository.delete(resetToken);
        refreshTokenRepository.deleteByUser(user);
    }

    @Override
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        // Generate verification token for mail confirmation
        passwordResetTokenRepository.deleteByUser(currentUser);

        String token = UUID.randomUUID().toString();
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(token)
                .expiryDate(Instant.now().plusSeconds(1800)) // 30 minutes
                .user(currentUser)
                .build();

        passwordResetTokenRepository.save(verificationToken);

        // Send confirmation email
        mailService.sendPasswordChangeVerificationEmail(currentUser.getEmail(), token);
    }

    @Override
    @Transactional
    public void confirmPasswordChange(String tokenStr, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(token);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = token.getUser();
        userService.updatePassword(user, newPassword);

        // Clean up
        passwordResetTokenRepository.delete(token);
        refreshTokenRepository.deleteByUser(user);
    }
}
