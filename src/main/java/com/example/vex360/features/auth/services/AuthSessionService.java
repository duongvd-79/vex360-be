package com.example.vex360.features.auth.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Owns access-token issuance and the persistent refresh-token lifecycle.
 * Enforces single-session issuance, refresh-token rotation, replay detection,
 * and session revocation.
 */
@Service
@Slf4j
public class AuthSessionService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final Clock clock;
    private final long refreshExpirationMs;

    public AuthSessionService(
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenRevocationService accessTokenRevocationService,
            Clock clock,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenRevocationService = accessTokenRevocationService;
        this.clock = clock;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Replaces any existing refresh-token sessions for a user with a new token
     * pair.
     *
     * @param user authenticated user receiving the session
     * @return newly issued access and refresh tokens
     */
    @Transactional
    public TokenResponse issue(User user) {
        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = newRefreshToken(user, refreshTokenValue);

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(refreshToken);
        return tokenResponse(accessToken, refreshTokenValue);
    }

    /**
     * Consumes a refresh token exactly once and issues its replacement. Reuse of
     * an already consumed token revokes every session belonging to its user.
     *
     * @param tokenValue refresh token to rotate
     * @return replacement access and refresh tokens
     * @throws AppException when the token is missing, expired, or already used
     */
    @Transactional(noRollbackFor = AppException.class)
    public TokenResponse rotate(String tokenValue) {
        RefreshToken currentToken = refreshTokenRepository.findByTokenForUpdate(tokenValue)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        User user = currentToken.getUser();

        if (currentToken.isUsed()) {
            log.error("SECURITY ALERT: Refresh token reuse detected; revoking all sessions for user: {}",
                    user.getEmail());
            refreshTokenRepository.deleteByUser(user);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (currentToken.getExpiryDate().isBefore(Instant.now(clock))) {
            refreshTokenRepository.delete(currentToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        currentToken.setUsed(true);
        refreshTokenRepository.save(currentToken);

        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        String refreshTokenValue = UUID.randomUUID().toString();
        refreshTokenRepository.save(newRefreshToken(user, refreshTokenValue));
        return tokenResponse(accessToken, refreshTokenValue);
    }

    /**
     * Revokes one refresh token and blacklists the corresponding access token when
     * available.
     *
     * @param refreshTokenValue refresh token to revoke
     * @param accessToken       active access token, or {@code null} when
     *                          unavailable
     */
    @Transactional
    public void logout(String refreshTokenValue, String accessToken) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(refreshTokenRepository::delete);
        accessTokenRevocationService.blacklist(accessToken);
    }

    /**
     * Revokes every refresh-token session for a user and blacklists the current
     * access token when available.
     *
     * @param user        owner of the sessions to revoke
     * @param accessToken active access token, or {@code null} when unavailable
     */
    @Transactional
    public void revokeAll(User user, String accessToken) {
        refreshTokenRepository.deleteByUser(user);
        accessTokenRevocationService.blacklist(accessToken);
    }

    private RefreshToken newRefreshToken(User user, String tokenValue) {
        return RefreshToken.builder()
                .token(tokenValue)
                .expiryDate(Instant.now(clock).plusMillis(refreshExpirationMs))
                .user(user)
                .used(false)
                .build();
    }

    private TokenResponse tokenResponse(String accessToken, String refreshToken) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
