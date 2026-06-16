package com.example.vex360.shared.exceptions;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonPropertyOrder({
        "timestamp",
        "status",
        "error",
        "code",
        "message",
        "path",
        "validationErrors"
})
@JsonInclude(JsonInclude.Include.NON_NULL) // Trường nào null thì không gửi về Client
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;          // Ví dụ: 404, 400
    private String error;        // HTTP Status Name (Ví dụ: NOT_FOUND)
    private String code;         // Business Code (Ví dụ: USER-001)
    private String message;      // Thông điệp lỗi dễ hiểu cho người dùng
    private String path;         // API Endpoint xảy ra lỗi
    
    // Phục vụ riêng cho lỗi Validation từ RequestBody (nhiều trường lỗi cùng lúc)
    private List<ValidationError> validationErrors;

    @Data
    @Builder
    public static class ValidationError {
        private String field;
        private String message;
    }
}
