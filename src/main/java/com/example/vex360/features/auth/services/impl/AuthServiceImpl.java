package com.example.vex360.features.auth.services.impl;

import java.time.Instant;
import java.util.Map;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
    private final JwtService jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Value("${app.security.oauth2.client-id}")
    private String googleClientId;

    @Value("${app.security.oauth2.client-secret}")
    private String googleClientSecret;

    @Value("${app.security.oauth2.redirect-uri}")
    private String googleRedirectUri;

    @Value("${app.security.oauth2.user-info-uri}")
    private String googleUserInfoUri;

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

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                googleUserInfoUri,
                HttpMethod.GET,
                new HttpEntity<>(userInfoHeaders),
                Map.class);

        Map<String, Object> userInfo = userInfoResponse.getBody();
        if (userInfo == null || !userInfo.containsKey("email")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email    = (String) userInfo.get("email");
        String fullName = (String) userInfo.get("name");
        String avatar   = (String) userInfo.get("picture");

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

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        UserRequestDTO userRequest = authMapper.toUserRequestDTO(request);
        userService.createUser(userRequest);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // Generate Access Token
        String accessToken = jwtProvider.generateToken(userDetails);

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
        String newAccessToken = jwtProvider.generateToken(new CustomUserDetails(user));

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
