package com.example.vex360.shared.config.jwt;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.config.security.TenantContext;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.security.strict-mode:true}")
    private boolean strictMode;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // In strict mode, reject blacklisted tokens immediately
                if (strictMode && tokenBlacklistService.isBlacklisted(jwt)) {
                    log.warn("Access attempt with a blacklisted token: {}", request.getRequestURI());
                    filterChain.doFilter(request, response);
                    return;
                }

                // Parse and validate the JWT token once
                Claims claims = jwtService.extractAllClaims(jwt);
                String email = claims.getSubject();
                if (StringUtils.hasText(email)) {
                    String userIdStr = claims.get("userId", String.class);

                    if (StringUtils.hasText(userIdStr)) {
                        String roleStr = claims.get("role", String.class);
                        String statusStr = claims.get("status", String.class);

                        User user = User.builder()
                                .id(UUID.fromString(userIdStr))
                                .email(email)
                                .role(Role.valueOf(roleStr))
                                .status(UserStatus.valueOf(statusStr))
                                .build();

                        CustomUserDetails userDetails = new CustomUserDetails(user);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        // Set TenantContext if the role is ORGANIZER
                        if (user.getRole() == Role.ORGANIZER) {
                            String tenantIdStr = claims.get("tenantId", String.class);
                            if (StringUtils.hasText(tenantIdStr)) {
                                TenantContext.setCurrentTenantId(UUID.fromString(tenantIdStr));
                            }
                        }
                    } else {
                        log.warn("Authentication rejected: JWT token is missing required claims (userId).");
                    }
                }
            }
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: {}", ex.getMessage());
            filterChain.doFilter(request, response);
        } catch (SignatureException | MalformedJwtException ex) {
            log.warn("Invalid JWT format/signature: {}", ex.getMessage());
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
