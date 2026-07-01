package com.example.vex360.features.auth.services.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.example.vex360.features.auth.entities.RegistrationToken;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.repositories.RegistrationTokenRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RegistrationTokenRepository registrationTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .lockoutEnd(null)
                .build();

        // Inject @Value fields
        setField(authService, "refreshExpirationMs", 3600000L);
        setField(authService, "googleClientId", "google-client-id");
        setField(authService, "googleClientSecret", "google-client-secret");
        setField(authService, "googleRedirectUri", "google-redirect-uri");
        setField(authService, "googleUserInfoUri", "google-user-info-uri");
        setField(authService, "backendBaseUrl", "http://localhost:8080");

        // Set static key for TokenEncryptionUtils to be stable
        TokenEncryptionUtils.setKey("TestSecretKey1234");
    }

    // ==========================================
    // loginWithGoogle Tests
    // ==========================================

    @Test
    void loginWithGoogle_Success() {
        Map<String, Object> mockTokenResponse = new HashMap<>();
        mockTokenResponse.put("access_token", "google-access-token");

        Map<String, Object> mockUserInfo = new HashMap<>();
        mockUserInfo.put("email", "test@example.com");
        mockUserInfo.put("name", "Test User");
        mockUserInfo.put("picture", "avatar.png");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(mockTokenResponse);
                    when(mock.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(ResponseEntity.ok(mockUserInfo));
                })) {

            when(userService.findOrCreateGoogleUser("test@example.com", "Test User", "avatar.png"))
                    .thenReturn(testUser);
            when(jwtProvider.generateToken(any(CustomUserDetails.class))).thenReturn("access-token-123");

            TokenResponse response = authService.loginWithGoogle("oauth-code");

            assertNotNull(response);
            assertEquals("access-token-123", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            verify(refreshTokenRepository).deleteByUser(testUser);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }
    }

    @Test
    void loginWithGoogle_TokenResponseNull_ThrowsUnauthenticated() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(null);
                })) {

            AppException ex = assertThrows(AppException.class, () -> authService.loginWithGoogle("oauth-code"));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        }
    }

    @Test
    void loginWithGoogle_TokenResponseNoAccessToken_ThrowsUnauthenticated() {
        Map<String, Object> mockTokenResponse = new HashMap<>(); // missing access_token

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(mockTokenResponse);
                })) {

            AppException ex = assertThrows(AppException.class, () -> authService.loginWithGoogle("oauth-code"));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        }
    }

    @Test
    void loginWithGoogle_UserInfoNull_ThrowsUnauthenticated() {
        Map<String, Object> mockTokenResponse = new HashMap<>();
        mockTokenResponse.put("access_token", "google-access-token");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(mockTokenResponse);
                    when(mock.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(ResponseEntity.ok(null));
                })) {

            AppException ex = assertThrows(AppException.class, () -> authService.loginWithGoogle("oauth-code"));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        }
    }

    @Test
    void loginWithGoogle_UserInfoNoEmail_ThrowsUnauthenticated() {
        Map<String, Object> mockTokenResponse = new HashMap<>();
        mockTokenResponse.put("access_token", "google-access-token");

        Map<String, Object> mockUserInfo = new HashMap<>(); // missing email

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(mockTokenResponse);
                    when(mock.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                            .thenReturn(ResponseEntity.ok(mockUserInfo));
                })) {

            AppException ex = assertThrows(AppException.class, () -> authService.loginWithGoogle("oauth-code"));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        }
    }

    // ==========================================
    // register Tests
    // ==========================================

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("register@example.com");
        request.setPassword("password");

        UserRequestDTO userRequest = new UserRequestDTO();
        userRequest.setEmail("register@example.com");
        userRequest.setPassword("password");

        User pendingUser = User.builder()
                .id(UUID.randomUUID())
                .email("register@example.com")
                .status(UserStatus.PENDING)
                .build();

        when(authMapper.toUserRequestDTO(request)).thenReturn(userRequest);
        when(userService.createUser(userRequest, UserStatus.PENDING)).thenReturn(pendingUser);

        authService.register(request);

        verify(registrationTokenRepository).deleteByUser(pendingUser);
        verify(registrationTokenRepository).save(any(RegistrationToken.class));
        verify(mailService).sendRegistrationVerificationEmail(eq("register@example.com"), anyString());
    }

    // ==========================================
    // verifyRegistration Tests
    // ==========================================

    @Test
    void verifyRegistration_Success() {
        String rawToken = "verify-token-123";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        RegistrationToken registrationToken = RegistrationToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().plusSeconds(60))
                .user(testUser)
                .build();

        when(registrationTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(registrationToken));

        authService.verifyRegistration(encryptedToken);

        verify(userService).updateStatus(testUser.getId(), UserStatus.ACTIVE);
        verify(registrationTokenRepository).delete(registrationToken);
    }

    @Test
    void verifyRegistration_TokenNotFound_ThrowsUnauthenticated() {
        String encryptedToken = TokenEncryptionUtils.encrypt("not-found-token");

        when(registrationTokenRepository.findByToken("not-found-token")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.verifyRegistration(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(userService, never()).updateStatus(any(), any());
    }

    @Test
    void verifyRegistration_TokenExpired_ThrowsUnauthenticated() {
        String rawToken = "expired-token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        RegistrationToken registrationToken = RegistrationToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().minusSeconds(10))
                .user(testUser)
                .build();

        when(registrationTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(registrationToken));

        AppException ex = assertThrows(AppException.class, () -> authService.verifyRegistration(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(registrationTokenRepository).delete(registrationToken);
        verify(userService, never()).updateStatus(any(), any());
    }

    // ==========================================
    // login Tests
    // ==========================================

    @Test
    void login_Success_NoFailedAttempts() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        testUser.setFailedLoginAttempts(0);

        when(userService.findUserByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtProvider.generateToken(userDetails)).thenReturn("mock-access-token");

        TokenResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        verify(userService, never()).resetFailedAttempts(any());
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_Success_WithFailedAttempts() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        testUser.setFailedLoginAttempts(3);

        when(userService.findUserByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtProvider.generateToken(userDetails)).thenReturn("mock-access-token");

        TokenResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        verify(userService).resetFailedAttempts(testUser);
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_AccountLocked_ThrowsAccountLocked() {
        LoginRequest request = new LoginRequest("locked@example.com", "password");
        User lockedUser = User.builder()
                .email("locked@example.com")
                .lockoutEnd(Instant.now().plusSeconds(60))
                .build();

        when(userService.findUserByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_BadCredentials_ThrowsBadCredentialsAndIncrementsAttempts() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong-password");

        when(userService.findUserByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new AuthenticationException("Bad credentials") {});

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
        verify(userService).incrementFailedAttempts("test@example.com");
    }

    // ==========================================
    // refreshToken Tests
    // ==========================================

    @Test
    void refreshToken_Success() {
        String tokenStr = "valid-refresh-token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .used(false)
                .expiryDate(Instant.now().plusSeconds(60))
                .user(testUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));
        when(jwtProvider.generateToken(any(CustomUserDetails.class))).thenReturn("new-access-token");

        TokenResponse response = authService.refreshToken(tokenStr);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(refreshToken.isUsed());
        verify(refreshTokenRepository).save(refreshToken);
        verify(refreshTokenRepository).save(argThat(newToken -> !newToken.getToken().equals(tokenStr)));
    }

    @Test
    void refreshToken_NotFound_ThrowsUnauthenticated() {
        when(refreshTokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.refreshToken("invalid-token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void refreshToken_CompromisedReuse_EvictsSessionsAndThrowsUnauthenticated() {
        String tokenStr = "already-used-token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .used(true)
                .expiryDate(Instant.now().plusSeconds(60))
                .user(testUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));

        AppException ex = assertThrows(AppException.class, () -> authService.refreshToken(tokenStr));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshToken_Expired_DeletesTokenAndThrowsUnauthenticated() {
        String tokenStr = "expired-token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .used(false)
                .expiryDate(Instant.now().minusSeconds(10))
                .user(testUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));

        AppException ex = assertThrows(AppException.class, () -> authService.refreshToken(tokenStr));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(refreshTokenRepository).delete(refreshToken);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ==========================================
    // logout Tests
    // ==========================================

    @Test
    void logout_Success() {
        String tokenStr = "refresh-token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .user(testUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer access-token-xyz");

        Date expirationDate = new Date(System.currentTimeMillis() + 100000);
        when(jwtProvider.extractClaims(eq("access-token-xyz"), any())).thenReturn(expirationDate);

        authService.logout(tokenStr);

        verify(refreshTokenRepository).delete(refreshToken);
        verify(tokenBlacklistService).blacklistToken("access-token-xyz", expirationDate);
    }

    // ==========================================
    // forgotPassword Tests
    // ==========================================

    @Test
    void forgotPassword_EmailExists_SendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userService.findUserByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository).deleteByUser(testUser);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendForgotPasswordEmail(eq("test@example.com"), anyString());
    }

    @Test
    void forgotPassword_EmailDoesNotExist_SilentReturn() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");

        when(userService.findUserByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword(request));

        verify(passwordResetTokenRepository, never()).deleteByUser(any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendForgotPasswordEmail(any(), any());
    }

    // ==========================================
    // validateResetToken Tests
    // ==========================================

    @Test
    void validateResetToken_Success() {
        String rawToken = "reset-token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().plusSeconds(60))
                .user(testUser)
                .build();

        when(passwordResetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        assertDoesNotThrow(() -> authService.validateResetToken(encryptedToken));
    }

    @Test
    void validateResetToken_NotFound_ThrowsUnauthenticated() {
        String encryptedToken = TokenEncryptionUtils.encrypt("not-found");

        when(passwordResetTokenRepository.findByToken("not-found")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.validateResetToken(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void validateResetToken_Expired_ThrowsUnauthenticated() {
        String rawToken = "expired-reset-token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().minusSeconds(10))
                .user(testUser)
                .build();

        when(passwordResetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        AppException ex = assertThrows(AppException.class, () -> authService.validateResetToken(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    // ==========================================
    // resetPassword Tests
    // ==========================================

    @Test
    void resetPassword_Success() {
        String rawToken = "valid-reset-token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().plusSeconds(60))
                .user(testUser)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(encryptedToken, "new-password");

        when(passwordResetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer access-token-xyz");

        Date expirationDate = new Date(System.currentTimeMillis() + 100000);
        when(jwtProvider.extractClaims(eq("access-token-xyz"), any())).thenReturn(expirationDate);

        authService.resetPassword(request);

        verify(userService).updatePassword(testUser, "new-password");
        verify(passwordResetTokenRepository).delete(resetToken);
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(tokenBlacklistService).blacklistToken("access-token-xyz", expirationDate);
    }

    @Test
    void resetPassword_NotFound_ThrowsUnauthenticated() {
        String encryptedToken = TokenEncryptionUtils.encrypt("not-found");
        ResetPasswordRequest request = new ResetPasswordRequest(encryptedToken, "new-password");

        when(passwordResetTokenRepository.findByToken("not-found")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.resetPassword(request));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(userService, never()).updatePassword(any(), any());
    }

    @Test
    void resetPassword_Expired_DeletesTokenAndThrowsUnauthenticated() {
        String rawToken = "expired-token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(rawToken)
                .expiryDate(Instant.now().minusSeconds(10))
                .user(testUser)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(encryptedToken, "new-password");

        when(passwordResetTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(resetToken));

        AppException ex = assertThrows(AppException.class, () -> authService.resetPassword(request));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(passwordResetTokenRepository).delete(resetToken);
        verify(userService, never()).updatePassword(any(), any());
    }

    // ==========================================
    // changePassword Tests
    // ==========================================

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("old-password", "new-password");

        when(passwordEncoder.matches("old-password", testUser.getPassword())).thenReturn(true);
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer access-token-xyz");

        Date expirationDate = new Date(System.currentTimeMillis() + 100000);
        when(jwtProvider.extractClaims(eq("access-token-xyz"), any())).thenReturn(expirationDate);

        authService.changePassword(testUser, request);

        verify(userService).updatePassword(testUser, "new-password");
        verify(passwordResetTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(tokenBlacklistService).blacklistToken("access-token-xyz", expirationDate);
        verify(mailService).sendPasswordChangeNotificationEmail("test@example.com");
    }

    @Test
    void changePassword_OldPasswordMismatch_ThrowsValidationFailed() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong-old-password", "new-password");

        when(passwordEncoder.matches("wrong-old-password", testUser.getPassword())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.changePassword(testUser, request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(userService, never()).updatePassword(any(), any());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
