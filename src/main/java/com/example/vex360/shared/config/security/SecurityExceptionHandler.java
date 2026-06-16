package com.example.vex360.shared.config.security;

import java.io.IOException;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.exceptions.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        writeErrorResponse(request, response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        writeErrorResponse(request, response, ErrorCode.UNAUTHORIZED);
    }

    private void writeErrorResponse(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getHttpStatus().value())
                .error(errorCode.getHttpStatus().name())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(request.getRequestURI())
                .build();

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(errorResponse));
    }
}
