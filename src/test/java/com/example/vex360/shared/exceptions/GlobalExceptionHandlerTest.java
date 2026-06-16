package com.example.vex360.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.example.vex360.shared.dtos.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAccessDeniedExceptionReturnsUnauthorizedErrorResponse() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleAccessDeniedException(
                new AccessDeniedException("Forbidden"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Unauthorized");
    }
}
