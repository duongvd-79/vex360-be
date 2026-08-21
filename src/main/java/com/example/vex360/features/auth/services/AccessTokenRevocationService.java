package com.example.vex360.features.auth.services;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Blacklists access tokens after logout or credential changes.
 * <p>
 * Revocation is best-effort so an invalid or missing access token does not
 * prevent the primary session or password operation from completing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessTokenRevocationService {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Adds a valid access token to the blacklist until its JWT expiration time.
     * Missing or malformed tokens are ignored after being logged.
     *
     * @param accessToken JWT to blacklist, or {@code null} when unavailable
     */
    public void blacklist(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }

        try {
            Date expiration = jwtService.extractClaims(accessToken, Claims::getExpiration);
            tokenBlacklistService.blacklistToken(accessToken, expiration);
        } catch (Exception exception) {
            log.warn("Failed to blacklist active access token: {}", exception.getMessage());
        }
    }
}
