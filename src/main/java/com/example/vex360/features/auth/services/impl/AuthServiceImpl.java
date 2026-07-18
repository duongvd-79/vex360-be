package com.example.vex360.features.auth.services.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.auth.services.AuthSessionService;
import com.example.vex360.features.auth.services.GoogleOAuthClient;
import com.example.vex360.features.auth.services.GoogleOAuthClient.GoogleProfile;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates local and Google authentication, delegating token lifecycle
 * operations to {@link AuthSessionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;
    private final GoogleOAuthClient googleOAuthClient;
    private final Clock clock;

    /** {@inheritDoc} */
    @Override
    public TokenResponse loginWithGoogle(String code) {
        GoogleProfile profile = googleOAuthClient.exchangeCode(code);
        User user = userService.findOrCreateGoogleUser(
                profile.email(),
                profile.fullName(),
                profile.avatarUrl());
        return authSessionService.issue(user);
    }

    /** {@inheritDoc} */
    @Override
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userService.findUserByEmail(request.getEmail());
        if (user.isPresent()
                && user.get().getLockoutEnd() != null
                && user.get().getLockoutEnd().isAfter(Instant.now(clock))) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User authenticatedUser = userDetails.getUser();
            if (authenticatedUser.getFailedLoginAttempts() > 0) {
                userService.resetFailedAttempts(authenticatedUser);
            }
            return authSessionService.issue(authenticatedUser);
        } catch (AuthenticationException exception) {
            userService.incrementFailedAttempts(request.getEmail());
            log.warn("Authentication failed for email: {}", LogSanitizer.sanitize(request.getEmail()));
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }
    }

    /** {@inheritDoc} */
    @Override
    public TokenResponse refreshToken(String refreshToken) {
        return authSessionService.rotate(refreshToken);
    }

    /** {@inheritDoc} */
    @Override
    public void logout(String refreshToken, String accessToken) {
        authSessionService.logout(refreshToken, accessToken);
    }
}
