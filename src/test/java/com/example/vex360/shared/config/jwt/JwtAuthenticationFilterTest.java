package com.example.vex360.shared.config.jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    public void setup() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "strictMode", true);
    }

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testDoFilterInternal_ValidToken_Success() throws ServletException, IOException {
        String jwt = "valid.jwt.token";
        String email = "test@example.com";
        String userId = UUID.randomUUID().toString();
        Claims claims = mock(Claims.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        when(tokenBlacklistService.isBlacklisted(jwt)).thenReturn(false);
        when(jwtService.extractAllClaims(jwt)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(email);
        when(claims.get("userId", String.class)).thenReturn(userId);
        when(claims.get("role", String.class)).thenReturn("VISITOR");
        when(claims.get("status", String.class)).thenReturn("ACTIVE");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternal_BlacklistedToken() throws ServletException, IOException {
        String jwt = "blacklisted.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        when(tokenBlacklistService.isBlacklisted(jwt)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoMoreInteractions(jwtService);
    }

    @Test
    public void testDoFilterInternal_NoToken() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternal_ExpiredToken_WarnsAndContinues() throws ServletException, IOException {
        String jwt = "expired.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        when(tokenBlacklistService.isBlacklisted(jwt)).thenReturn(false);
        when(jwtService.extractAllClaims(jwt)).thenThrow(mock(ExpiredJwtException.class));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
