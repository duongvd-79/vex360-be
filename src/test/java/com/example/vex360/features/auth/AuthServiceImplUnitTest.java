package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.doThrow;

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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthSessionService;
import com.example.vex360.features.auth.services.GoogleOAuthClient;
import com.example.vex360.features.auth.services.GoogleOAuthClient.GoogleProfile;
import com.example.vex360.features.auth.services.impl.AuthServiceImpl;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private Authentication authentication;

    private Clock clock;
    private AuthServiceImpl authService;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneId.of("UTC"));
        authService = new AuthServiceImpl(userService, authenticationManager, authSessionService, googleOAuthClient, clock);

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded_pass")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(1)
                .build();
    }

    @Test
    void loginWithGoogle_Success() {
        GoogleProfile profile = new GoogleProfile("google@example.com", "Google User", "http://avatar");
        when(googleOAuthClient.exchangeCode("code123")).thenReturn(profile);
        when(userService.findOrCreateGoogleUser("google@example.com", "Google User", "http://avatar")).thenReturn(user);

        TokenResponse expected = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
        when(authSessionService.issue(user, true)).thenReturn(expected);

        TokenResponse response = authService.loginWithGoogle("code123");

        assertNotNull(response);
        assertEquals("access", response.getAccessToken());
        verify(authSessionService).issue(user, true);
    }

    @Test
    void login_AccountLocked_ThrowsAppException() {
        user.setLockoutEnd(Instant.parse("2026-08-01T11:00:00Z")); // Locked until 11:00 (now is 10:00)
        when(userService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("user@example.com", "pass", false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    }

    @Test
    void login_Success_ResetsFailedAttempts() {
        when(userService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        CustomUserDetails userDetails = new CustomUserDetails(user);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        TokenResponse expected = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
        when(authSessionService.issue(user, true)).thenReturn(expected);

        LoginRequest request = new LoginRequest("user@example.com", "pass", true);
        TokenResponse response = authService.login(request);

        assertNotNull(response);
        verify(userService).resetFailedAttempts(user);
        verify(authSessionService).issue(user, true);
    }

    @Test
    void login_AuthenticationException_IncrementsFailedAttemptsAndThrowsAppException() {
        when(userService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new BadCredentialsException("Bad creds"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        LoginRequest request = new LoginRequest("user@example.com", "wrongpass", false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
        verify(userService).incrementFailedAttempts("user@example.com");
    }

    @Test
    void refreshToken_Success() {
        TokenResponse expected = TokenResponse.builder().accessToken("new_access").refreshToken("new_refresh").build();
        when(authSessionService.rotate("ref_token")).thenReturn(expected);

        TokenResponse response = authService.refreshToken("ref_token");

        assertEquals(expected, response);
    }

    @Test
    void logout_Success() {
        authService.logout("ref_token", "acc_token");
        verify(authSessionService).logout("ref_token", "acc_token");
    }
}
