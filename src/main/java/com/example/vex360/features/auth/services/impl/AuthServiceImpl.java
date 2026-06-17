package com.example.vex360.features.auth.services.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.mapper.AuthMapper;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for handling authentication, token lifecycle, and password management.
 * Designed with OWASP secure coding principles in mind:
 * - Proper password hashing matching via PasswordEncoder (BCrypt/Argon2)
 * - Anti-Session-Replay via Refresh Token Rotation (RTR)
 * - Intrusion detection / compromise recovery via reuse detection
 * - Anti-Email-Enumeration in Forgot Password flow
 * - Access token blacklisting on logout and password modifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final TokenBlacklistService tokenBlacklistService;
    private final jakarta.servlet.http.HttpServletRequest httpServletRequest;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    /**
     * Registers a new user in the system.
     * Delegated to UserService which performs validation (e.g. duplicate email checks).
     *
     * @param request the registration details
     */
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        UserRequestDTO userRequest = authMapper.toUserRequestDTO(request);
        userService.createUser(userRequest);
    }

    /**
     * Authenticates a user and generates a stateful session key (Refresh Token) and a stateless token (Access Token).
     * Secures session initialization by removing any pre-existing sessions for the user to enforce single-device limits.
     *
     * @param request the user credentials (email and password)
     * @return the authentication token pair
     */
    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // Authenticates the user principal via Spring Security AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // 1. Generate Stateless Access Token (Contains claims for stateless authorization checks)
        String accessToken = jwtProvider.generateToken(userDetails);

        // 2. Generate and Save Refresh Token (High-entropy UUID session key)
        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .user(user)
                .used(false)
                .build();

        // Security Control: Remove existing sessions for this user before storing the new session
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(tokenStr)
                .build();
    }

    /**
     * Exchange an active, unused Refresh Token for a new Access and Refresh Token pair (Rotation).
     * Implements OWASP Token Lifecycle controls:
     * - Refresh Token Rotation (RTR): Each rotation invalidates the old token and issues a new one.
     * - Replay/Theft Detection: If an already-used Refresh Token is presented, the system detects a compromise,
     *   revokes all sessions for the associated user, and denies access.
     *
     * @param tokenStr the refresh token presented by the client
     * @return the new token pair
     */
    @Override
    @Transactional
    public TokenResponse refreshToken(String tokenStr) {
        // Retrieve the token from the DB. Reject if not found.
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = refreshToken.getUser();

        // 1. Theft Detection: If the token has already been marked as used, it signifies a replay attack
        if (refreshToken.isUsed()) {
            log.error("SECURITY ALERT: Compromised Refresh Token Reuse Detected! Revoking all sessions for user: {}", user.getEmail());
            // Defensive Action: Evict all active sessions for this user to protect the account
            refreshTokenRepository.deleteByUser(user);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // 2. Expiration Check
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // 3. Generate a new stateless Access Token
        String newAccessToken = jwtProvider.generateToken(new CustomUserDetails(user));

        // 4. Mark the current refresh token as used to implement rotation and theft detection
        refreshToken.setUsed(true);
        refreshTokenRepository.save(refreshToken);

        // 5. Generate and store the new Refresh Token for rotation
        String newRefreshTokenStr = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenStr)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .user(user)
                .used(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .build();
    }

    /**
     * Logs the user out by invalidating the refresh token session in the database
     * and blacklisting the currently active access token.
     *
     * @param tokenStr the refresh token session to invalidate
     */
    @Override
    @Transactional
    public void logout(String tokenStr) {
        refreshTokenRepository.findByToken(tokenStr).ifPresent(refreshTokenRepository::delete);
        blacklistCurrentToken();
    }

    /**
     * Initiates the forgot password flow.
     * Implements Anti-Email-Enumeration (OWASP A01 & A05):
     * - If the email does not exist, the API logs the event but returns silently without throwing an error to the client.
     * - This prevents attackers from mapping out active email registrations.
     *
     * @param request the password reset request details
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        try {
            User user = userService.getUserByEmail(request.getEmail());

            // Clear any existing reset tokens
            passwordResetTokenRepository.deleteByUser(user);

            // Generate high-entropy reset token with 1-hour expiration
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .expiryDate(Instant.now().plusSeconds(3600)) // 1 hour
                    .user(user)
                    .build();

            passwordResetTokenRepository.save(resetToken);

            // Send reset mail containing the link
            mailService.sendForgotPasswordEmail(user.getEmail(), token);
        } catch (AppException e) {
            // Anti-Email-Enumeration: return success silently even if user is not found
            if (e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                log.info("Forgot password requested for non-existent email (Anti-Enumeration active): {}", request.getEmail());
            } else {
                throw e;
            }
        }
    }

    /**
     * Resets a user's password using a valid, unexpired reset token.
     * Revokes all active refresh tokens and blacklists the current access token to terminate active attacker sessions.
     *
     * @param request the password reset details (token and new password)
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Check token expiration
        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = resetToken.getUser();
        userService.updatePassword(user, request.getNewPassword());

        // Invalidate token and revoke all active sessions to secure the account
        passwordResetTokenRepository.delete(resetToken);
        refreshTokenRepository.deleteByUser(user);
        blacklistCurrentToken();
    }

    /**
     * Initiates a password change request for the current authenticated user.
     * Verifies the old password against the database record before generating a verification token sent via email.
     *
     * @param currentUser the authenticated user principal
     * @param request the password change request details
     */
    @Override
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest request) {
        // Enforce credential verification prior to generating the change token
        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        passwordResetTokenRepository.deleteByUser(currentUser);

        // Generate 30-minute confirmation token
        String token = UUID.randomUUID().toString();
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(token)
                .expiryDate(Instant.now().plusSeconds(1800)) // 30 minutes
                .user(currentUser)
                .build();

        passwordResetTokenRepository.save(verificationToken);

        // Send confirmation link to user mail
        mailService.sendPasswordChangeVerificationEmail(currentUser.getEmail(), token);
    }

    /**
     * Confirms the password change using a valid confirmation token.
     * Revokes all active refresh tokens and blacklists the current access token.
     *
     * @param tokenStr the confirmation token
     * @param newPassword the new password
     */
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
        blacklistCurrentToken();
    }

    /**
     * Extracts the JWT access token from the Authorization request header.
     *
     * @return the extracted JWT token, or null if not found/invalid
     */
    private String getJwtFromRequest() {
        String bearerToken = httpServletRequest.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Blacklists the active JWT access token to prevent its reuse after logout/password change.
     */
    private void blacklistCurrentToken() {
        try {
            String jwt = getJwtFromRequest();
            if (jwt != null) {
                java.util.Date expiration = jwtProvider.extractClaims(jwt, Claims::getExpiration);
                tokenBlacklistService.blacklistToken(jwt, expiration);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist active access token: {}", e.getMessage());
        }
    }
}
