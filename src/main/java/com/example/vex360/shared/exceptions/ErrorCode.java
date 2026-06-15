package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Cấu hình ErrorCode không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Dữ liệu đầu vào không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),

    // AUTH ERRORS
    UNAUTHENTICATED("AUTH-001", "Chưa đăng nhập hoặc token hết hạn", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("AUTH-002", "Bạn không có quyền truy cập tính năng này", HttpStatus.FORBIDDEN),

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "Email này đã được sử dụng", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}