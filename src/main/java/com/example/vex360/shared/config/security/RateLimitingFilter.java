package com.example.vex360.shared.config.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.vex360.shared.exceptions.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.security.strict-mode:true}")
    private boolean strictMode;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static class RequestBucket {
        final long resetTime;
        final AtomicInteger count = new AtomicInteger(0);

        RequestBucket(long windowMs) {
            this.resetTime = System.currentTimeMillis() + windowMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > resetTime;
        }
    }

    private final Map<String, RequestBucket> generalLimitMap = new ConcurrentHashMap<>();
    private final Map<String, RequestBucket> authLimitMap = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60000; // 1 minute
    private static final int MAX_GENERAL_REQUESTS = 100;
    private static final int MAX_AUTH_REQUESTS = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!strictMode) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIP(request);
        String path = request.getRequestURI();

        boolean isAuthEndpoint = path.startsWith("/api/v1/auth/");
        int limit = isAuthEndpoint ? MAX_AUTH_REQUESTS : MAX_GENERAL_REQUESTS;
        Map<String, RequestBucket> limitMap = isAuthEndpoint ? authLimitMap : generalLimitMap;

        RequestBucket bucket = limitMap.compute(ip, (key, existingBucket) -> {
            if (existingBucket == null || existingBucket.isExpired()) {
                return new RequestBucket(WINDOW_MS);
            }
            return existingBucket;
        });

        if (bucket.count.incrementAndGet() > limit) {
            log.warn("Rate limit exceeded for IP: {} on path: {}. Limit: {}/min", ip, path, limit);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error(HttpStatus.TOO_MANY_REQUESTS.name())
                    .code("SYS-004")
                    .message("Too many requests. Please try again after 1 minute.")
                    .path(path)
                    .build();

            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
