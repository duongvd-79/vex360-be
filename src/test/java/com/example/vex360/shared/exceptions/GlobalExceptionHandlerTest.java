package com.example.vex360.shared.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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
}
