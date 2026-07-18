package com.example.vex360.features.auth.services.impl;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthSessionService;
import com.example.vex360.features.auth.services.GoogleOAuthClient;
import com.example.vex360.features.auth.services.GoogleOAuthClient.GoogleProfile;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

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

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService,
                authenticationManager,
                authSessionService,
                googleOAuthClient,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void loginWithGoogle_ExchangesProfileAndIssuesSession() {
        GoogleProfile profile = new GoogleProfile("user@example.com", "User", "avatar");
        User user = User.builder().email(profile.email()).build();
        TokenResponse expected = TokenResponse.builder().accessToken("access-token").build();
        when(googleOAuthClient.exchangeCode("authorization-code")).thenReturn(profile);
        when(userService.findOrCreateGoogleUser(profile.email(), profile.fullName(), profile.avatarUrl()))
                .thenReturn(user);
        when(authSessionService.issue(user)).thenReturn(expected);

        assertEquals(expected, authService.loginWithGoogle("authorization-code"));
    }

    @Test
    void login_LockedAccountIsRejectedBeforeAuthentication() {
        LoginRequest request = loginRequest();
        User user = User.builder().lockoutEnd(NOW.plusSeconds(60)).build();
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));

        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_ValidCredentialsResetFailuresAndIssueSession() {
        LoginRequest request = loginRequest();
        User user = User.builder().email(request.getEmail()).failedLoginAttempts(2).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        TokenResponse expected = TokenResponse.builder().accessToken("access-token").build();
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authSessionService.issue(user)).thenReturn(expected);

        assertEquals(expected, authService.login(request));
        verify(userService).resetFailedAttempts(user);
    }

    @Test
    void login_InvalidCredentialsReturnGenericErrorAndTrackFailure() {
        LoginRequest request = loginRequest();
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));

        assertEquals(ErrorCode.BAD_CREDENTIALS, exception.getErrorCode());
        verify(userService).incrementFailedAttempts(request.getEmail());
    }

    @Test
    void refreshAndLogoutDelegateToSessionService() {
        TokenResponse expected = TokenResponse.builder().refreshToken("new-refresh").build();
        when(authSessionService.rotate("refresh-token")).thenReturn(expected);

        assertEquals(expected, authService.refreshToken("refresh-token"));
        authService.logout("refresh-token", "access-token");

        verify(authSessionService).logout("refresh-token", "access-token");
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");
        return request;
    }
}
