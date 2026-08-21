package com.example.vex360.features.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.services.AccessTokenRevocationService;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceUnitTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AccessTokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        revocationService = new AccessTokenRevocationService(jwtService, tokenBlacklistService);
    }

    @Test
    void blacklist_NullOrBlank_DoesNothing() {
        revocationService.blacklist(null);
        revocationService.blacklist("   ");

        verify(tokenBlacklistService, never()).blacklistToken(any(), any());
    }

    @Test
    void blacklist_ValidToken_BlacklistsSuccessfully() {
        Date expiration = new Date();
        when(jwtService.extractClaims(eq("valid_token"), any())).thenReturn(expiration);

        revocationService.blacklist("valid_token");

        verify(tokenBlacklistService).blacklistToken("valid_token", expiration);
    }

    @Test
    void blacklist_ExceptionThrown_CatchesAndLogsWarning() {
        when(jwtService.extractClaims(eq("invalid_token"), any())).thenThrow(new RuntimeException("Malformed token"));

        revocationService.blacklist("invalid_token");

        verify(tokenBlacklistService, never()).blacklistToken(any(), any());
    }
}
