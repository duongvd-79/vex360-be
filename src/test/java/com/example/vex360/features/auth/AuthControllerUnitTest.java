package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.auth.controllers.AuthController;
import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.GoogleCallbackRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.auth.services.PasswordService;
import com.example.vex360.features.auth.services.RegistrationService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.ApiResponse;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private AuthService authService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private PasswordService passwordService;

    private AuthController authController;
    private User user;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, registrationService, passwordService);
        ReflectionTestUtils.setField(authController, "resetPasswordFrontendUrl", "http://frontend/reset-password");
        ReflectionTestUtils.setField(authController, "registrationFrontendUrl", "http://frontend/login");
        ReflectionTestUtils.setField(authController, "rememberRefreshExpirationMs", 2592000000L);

        user = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password123!", "Test User", "0912345678");

        ResponseEntity<ApiResponse<Void>> response = authController.register(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(registrationService).register(request);
    }

    @Test
    void verifyRegistration_Success_RedirectsToFrontend() {
        ResponseEntity<Void> response = authController.verifyRegistration("valid_token");

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create("http://frontend/login?verified=true"), response.getHeaders().getLocation());
        verify(registrationService).verifyRegistration("valid_token");
    }

    @Test
    void login_Success_ReturnsTokenWithCookie() {
        LoginRequest request = new LoginRequest("user@example.com", "pass", true);
        TokenResponse tokens = TokenResponse.builder().accessToken("access").refreshToken("refresh").rememberMe(true)
                .build();

        when(authService.login(request)).thenReturn(tokens);

        ResponseEntity<ApiResponse<TokenResponse>> response = authController.login(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().get("Set-Cookie"));
        assertEquals(tokens, response.getBody().data());
    }

    @Test
    void googleCallback_Success() {
        GoogleCallbackRequest request = new GoogleCallbackRequest("code123");
        TokenResponse tokens = TokenResponse.builder().accessToken("access").refreshToken("refresh").rememberMe(false)
                .build();

        when(authService.loginWithGoogle("code123")).thenReturn(tokens);

        ResponseEntity<ApiResponse<TokenResponse>> response = authController.googleCallback(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tokens, response.getBody().data());
    }

    @Test
    void refreshToken_Success() {
        TokenResponse tokens = TokenResponse.builder().accessToken("new_access").refreshToken("new_refresh")
                .rememberMe(false).build();
        when(authService.refreshToken("cookie_refresh_token")).thenReturn(tokens);

        ResponseEntity<ApiResponse<TokenResponse>> response = authController.refreshToken("cookie_refresh_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tokens, response.getBody().data());
    }

    @Test
    void logout_Success_ClearsCookie() {
        ResponseEntity<ApiResponse<Void>> response = authController.logout("cookie_refresh_token",
                "Bearer access_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).logout("cookie_refresh_token", "access_token");
        assertNotNull(response.getHeaders().get("Set-Cookie"));
    }

    @Test
    void forgotPassword_Success() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");

        ResponseEntity<ApiResponse<Void>> response = authController.forgotPassword(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(passwordService).forgotPassword(request);
    }

    @Test
    void validateResetToken_Success_RedirectsToFrontend() {
        ResponseEntity<Void> response = authController.validateResetToken("valid_reset_token");

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getLocation().toString()
                .contains("http://frontend/reset-password?token=valid_reset_token"));
        verify(passwordService).validateResetToken("valid_reset_token");
    }

    @Test
    void resetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "NewPass123!");

        ResponseEntity<ApiResponse<Void>> response = authController.resetPassword(request, "Bearer access_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(passwordService).resetPassword(request, "access_token");
    }

    @Test
    void changePassword_Success() {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass", "newPass");

        ResponseEntity<ApiResponse<Void>> response = authController.changePassword(userDetails, request,
                "Bearer access_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(passwordService).changePassword(user.getId(), request, "access_token");
    }
}
