package com.example.vex360.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAccessDeniedExceptionReturnsUnauthorizedErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");

        var response = handler.handleAccessDeniedException(
                new AccessDeniedException("Forbidden"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getError()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getCode()).isEqualTo("AUTH-002");
        assertThat(response.getBody().getMessage()).isEqualTo("Không có quyền truy cập");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    void handleHttpMediaTypeNotSupportedExceptionReturnsValidationErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/products");

        var response = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException("Content-Type 'application/octet-stream' is not supported"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SYS-003");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/products");
    }

    @Test
    void handleMissingServletRequestPartExceptionReturnsValidationErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/products");

        var response = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("thumbnail"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SYS-003");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/products");
    }
}
