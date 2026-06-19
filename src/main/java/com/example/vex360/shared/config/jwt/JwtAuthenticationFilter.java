package com.example.vex360.shared.config.jwt;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

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
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.security.whitelist}")
    private String[] whitelist;

    @Value("${app.security.strict-mode:true}")
    private boolean strictMode;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        if (whitelist != null) {
            for (String pattern : whitelist) {
                if (pathMatcher.match(pattern.trim(), path)) {
                    return true;
                }
            }
        }
        return false;
    }

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

                String email = jwtService.extractEmailFromToken(jwt);
                if (StringUtils.hasText(email)) {
                    String userIdStr = null;
                    try {
                        userIdStr = jwtService.extractUserId(jwt);
                    } catch (Exception e) {
                        // Keep null if claim not present (e.g. in older tokens or mock tests)
                    }

                    if (StringUtils.hasText(userIdStr)) {
                        if (jwtService.validateToken(jwt)) {
                            String roleStr = jwtService.extractRole(jwt);
                            String statusStr = jwtService.extractStatus(jwt);

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
                        }
                    } else if (!strictMode) {
                        // Fallback to database check for backward compatibility / tests (Only in non-strict mode)
                        log.debug("No userId claim found. Falling back to DB load in non-strict development mode.");
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (userDetails != null && jwtService.validateToken(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    } else {
                        log.warn("Authentication rejected in strict mode: JWT token is missing required claims (userId).");
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
