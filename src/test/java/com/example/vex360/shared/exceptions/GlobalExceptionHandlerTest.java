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

    @Test
    void incompatibleBoothTemplateReturnsBadRequestContract() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/exhibitor/booths/booth-id/templates/template-id/apply");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleAppException(new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_COMPATIBLE), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("BOOTH-014", response.getBody().getCode());
        assertEquals(ErrorCode.BOOTH_TEMPLATE_NOT_COMPATIBLE.getMessage(), response.getBody().getMessage());
    }
}
