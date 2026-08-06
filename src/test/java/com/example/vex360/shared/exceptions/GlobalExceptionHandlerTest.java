package com.example.vex360.shared.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void oversizedMultipartReturnsFileSizeExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/booths/templates/id/panoramas");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleMaxUploadSizeExceededException(request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("FILE-001", response.getBody().getCode());
    }

    @Test
    void jsonSerializationFailureReturnsInternalServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        HttpMessageNotWritableException exception = new HttpMessageNotWritableException(
                "Could not write JSON", new IOException("JSON serialization failed"));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHttpMessageNotWritableException(exception, request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(ErrorCode.UNCATCHED_EXCEPTION.getCode(), response.getBody().getCode());
    }

    @Test
    void brokenPipeIsHandledWithoutWritingAnotherResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        HttpMessageNotWritableException exception = new HttpMessageNotWritableException(
                "ServletOutputStream failed to write", new IOException("Broken pipe"));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHttpMessageNotWritableException(exception, request);

        assertNull(response);
    }
}
