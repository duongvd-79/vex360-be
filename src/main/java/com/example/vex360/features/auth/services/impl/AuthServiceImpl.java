package com.example.vex360.features.auth.services.impl;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
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
import com.example.vex360.features.auth.entities.RegistrationToken;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.repositories.RegistrationTokenRepository;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.LogSanitizer;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for handling authentication, token lifecycle, and
 * password management.
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
    private final RegistrationTokenRepository registrationTokenRepository;
    private final MailService mailService;
    private final TokenBlacklistService tokenBlacklistService;
    private final jakarta.servlet.http.HttpServletRequest httpServletRequest;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.security.oauth2.client-id}")
    private String googleClientId;

    @Value("${app.security.oauth2.client-secret}")
    private String googleClientSecret;

    @Value("${app.security.oauth2.redirect-uri}")
    private String googleRedirectUri;

    @Value("${app.security.oauth2.user-info-uri}")
    private String googleUserInfoUri;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @Override
    @Transactional
    public TokenResponse loginWithGoogle(String code) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. Đổi authorization code lấy Google access token
        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("code", code);
        tokenParams.add("client_id", googleClientId);
        tokenParams.add("client_secret", googleClientSecret);
        tokenParams.add("redirect_uri", googleRedirectUri);
        tokenParams.add("grant_type", "authorization_code");

        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.postForObject(
                GOOGLE_TOKEN_URI,
                new HttpEntity<>(tokenParams, tokenHeaders),
                Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String googleAccessToken = (String) tokenResponse.get("access_token");

        // 2. Lấy thông tin người dùng từ Google
        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(googleAccessToken);

        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                googleUserInfoUri,
                HttpMethod.GET,
                new HttpEntity<>(userInfoHeaders),
                Map.class);

        Map<String, Object> userInfo = userInfoResponse.getBody();
        if (userInfo == null || !userInfo.containsKey("email")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email = (String) userInfo.get("email");
        String fullName = (String) userInfo.get("name");
        String avatar = (String) userInfo.get("picture");

        // 3. Tìm hoặc tạo mới user với provider GOOGLE
        User user = userService.findOrCreateGoogleUser(email, fullName, avatar);

        // 4. Phát sinh JWT
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtProvider.generateToken(userDetails);

        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .user(user)
                .build();

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(tokenStr)
                .build();
    }

    /**
     * Registers a new user in the system.
     * Delegated to UserService which performs validation (e.g. duplicate email
     * checks).
     *
     * @param request the registration details
     */
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        UserRequestDTO userRequest = authMapper.toUserRequestDTO(request);
        // Create user with PENDING status
        User user = userService.createUser(userRequest, UserStatus.PENDING);

        // Clear any existing registration tokens for safety
        registrationTokenRepository.deleteByUser(user);

        // Generate registration verification token (valid for 24 hours)
        String token = UUID.randomUUID().toString();
        RegistrationToken registrationToken = RegistrationToken.builder()
                .token(token)
                .expiryDate(Instant.now().plusSeconds(86400)) // 24 hours
                .user(user)
                .build();
        registrationTokenRepository.save(registrationToken);

        // Encrypt the token to keep it secure and compact in the link
        String encryptedToken = TokenEncryptionUtils.encrypt(token);
        String verifyUrl = backendBaseUrl + "/api/v1/auth/register/verify?token=" + encryptedToken;

        // Send registration verification HTML email
        mailService.sendRegistrationVerificationEmail(user.getEmail(), verifyUrl);
    }

    /**
     * Verifies the user registration using the encrypted token from email.
     * Activates the user status if the token is valid and unexpired.
     *
     * @param encryptedToken the encrypted registration token
     */
    @Override
    @Transactional
    public void verifyRegistration(String encryptedToken) {
        String rawToken = TokenEncryptionUtils.decrypt(encryptedToken);
        RegistrationToken registrationToken = registrationTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Check if token has expired
        if (registrationToken.getExpiryDate().isBefore(Instant.now())) {
            registrationTokenRepository.delete(registrationToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = registrationToken.getUser();
        // Activate user
        userService.updateStatus(user.getId(), UserStatus.ACTIVE);

        // Clean up verification token
        registrationTokenRepository.delete(registrationToken);
    }

    /**
     * Authenticates a user and generates a stateful session key (Refresh Token) and
     * a stateless token (Access Token).
     * Secures session initialization by removing any pre-existing sessions for the
     * user to enforce single-device limits.
     *
     * @param request the user credentials (email and password)
     * @return the authentication token pair
     */
    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 1. Check if the user account is currently locked (only check if user exists
        // to prevent enumeration)
        Optional<User> userOpt = userService.findUserByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getLockoutEnd() != null && user.getLockoutEnd().isAfter(Instant.now())) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
        }

        try {
            // Authenticates the user principal via Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // 2. Reset failed attempts count on successful login
            if (user.getFailedLoginAttempts() > 0) {
                userService.resetFailedAttempts(user);
            }

            // 3. Generate Stateless Access Token (Contains claims for stateless
            // authorization checks)
            String accessToken = jwtProvider.generateToken(userDetails);

            // 4. Generate and Save Refresh Token (High-entropy UUID session key)
            String tokenStr = UUID.randomUUID().toString();
            RefreshToken refreshToken = RefreshToken.builder()
                    .token(tokenStr)
                    .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                    .user(user)
                    .used(false)
                    .build();

            // Security Control: Remove existing sessions for this user before storing the
            // new session
            refreshTokenRepository.deleteByUser(user);
            refreshTokenRepository.save(refreshToken);

            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(tokenStr)
                    .build();
        } catch (AuthenticationException e) {
            // 5. Increment failed attempts count and log safely
            userService.incrementFailedAttempts(request.getEmail());
            log.warn("Authentication failed for email: {}", LogSanitizer.sanitize(request.getEmail()));
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }
    }

    /**
     * Exchange an active, unused Refresh Token for a new Access and Refresh Token
     * pair (Rotation).
     * Implements OWASP Token Lifecycle controls:
     * - Refresh Token Rotation (RTR): Each rotation invalidates the old token and
     * issues a new one.
     * - Replay/Theft Detection: If an already-used Refresh Token is presented, the
     * system detects a compromise,
     * revokes all sessions for the associated user, and denies access.
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

        // 1. Theft Detection: If the token has already been marked as used, it
        // signifies a replay attack
        if (refreshToken.isUsed()) {
            log.error("SECURITY ALERT: Compromised Refresh Token Reuse Detected! Revoking all sessions for user: {}",
                    user.getEmail());
            // Defensive Action: Evict all active sessions for this user to protect the
            // account
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

        // 4. Mark the current refresh token as used to implement rotation and theft
        // detection
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
     * - If the email does not exist, the API logs the event but returns silently
     * without throwing an error to the client.
     * - This prevents attackers from mapping out active email registrations.
     *
     * @param request the password reset request details
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt = userService.findUserByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            log.info("Forgot password requested for non-existent email (Anti-Enumeration active): {}",
                    LogSanitizer.sanitize(request.getEmail()));
            return;
        }

        User user = userOpt.get();
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

        // Encrypt token for the link to keep it secure and compact
        String encryptedToken = TokenEncryptionUtils.encrypt(token);
        String resetUrl = backendBaseUrl + "/api/v1/auth/reset-password/validate?token=" + encryptedToken;

        // Send reset mail containing the link
        mailService.sendForgotPasswordEmail(user.getEmail(), resetUrl);
    }

    /**
     * Validates if the given encrypted reset token is valid and not expired.
     *
     * @param encryptedToken the encrypted reset token from the URL
     */
    @Override
    @Transactional(readOnly = true)
    public void validateResetToken(String encryptedToken) {
        String rawToken = TokenEncryptionUtils.decrypt(encryptedToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /**
     * Resets a user's password using a valid, unexpired reset token.
     * Revokes all active refresh tokens and blacklists the current access token to
     * terminate active attacker sessions.
     *
     * @param request the password reset details (token and new password)
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String rawToken = TokenEncryptionUtils.decrypt(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(rawToken)
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
     * Changes a user's password directly after validating the old password.
     * Revokes all active sessions for this user and blacklists the current access
     * token.
     * Dispatches a notification email to the user.
     *
     * @param currentUser the authenticated user principal
     * @param request     the password change request details
     */
    @Override
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest request) {
        // Enforce credential verification prior to changing password
        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        // Update password immediately
        userService.updatePassword(currentUser, request.getNewPassword());

        // Invalidate reset tokens, revoke refresh tokens, and blacklist current access
        // token
        passwordResetTokenRepository.deleteByUser(currentUser);
        refreshTokenRepository.deleteByUser(currentUser);
        blacklistCurrentToken();

        // Send notification email
        mailService.sendPasswordChangeNotificationEmail(currentUser.getEmail());
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
     * Blacklists the active JWT access token to prevent its reuse after
     * logout/password change.
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
