package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.config.jwt.TokenBlacklistService;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void blacklist_ValidTokenStoresItsExpiration() {
        Date expiration = new Date(System.currentTimeMillis() + 60_000);
        when(jwtService.extractClaims(any(), any())).thenReturn(expiration);
        AccessTokenRevocationService service = service();

        service.blacklist("access-token");

        verify(tokenBlacklistService).blacklistToken("access-token", expiration);
    }

    @Test
    void blacklist_MissingTokenDoesNothing() {
        service().blacklist(null);

        verify(jwtService, never()).extractClaims(any(), any());
    }

    @Test
    void blacklist_MalformedTokenDoesNotBreakLogoutOrPasswordFlow() {
        when(jwtService.extractClaims(any(), any())).thenThrow(new IllegalArgumentException("invalid JWT"));

        assertDoesNotThrow(() -> service().blacklist("malformed-token"));

        verify(tokenBlacklistService, never()).blacklistToken(any(), any());
    }

    private AccessTokenRevocationService service() {
        return new AccessTokenRevocationService(jwtService, tokenBlacklistService);
    }
}
