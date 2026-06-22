package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Lỗi cấu hình ErrorCode", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Lỗi validation", HttpStatus.UNPROCESSABLE_ENTITY),

    // AUTH ERRORS
    UNAUTHENTICATED("AUTH-001", "Lỗi xác thực hoặc token hết hạn", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("AUTH-002", "Không có quyền truy cập", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS("AUTH-003", "Sai tài khoản hoặc mật khẩu", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH-004", "Tài khoản đã bị khóa", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("AUTH-005", "Tài khoản bị vô hiệu hóa", HttpStatus.UNAUTHORIZED),
    ACCOUNT_PENDING("AUTH-006", "Tài khoản chưa được kích hoạt", HttpStatus.UNAUTHORIZED),

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "The email already exists", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("USER-003", "Role not found", HttpStatus.NOT_FOUND),
    FILE_SIZE_EXCEEDED("FILE-001", "File size exceeded", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_SUPPORTED("FILE-002", "File type not supported", HttpStatus.BAD_REQUEST),
    NOT_A_PANORAMA("FILE-003", "Not a panorama", HttpStatus.BAD_REQUEST),
    UPLOAD_FAILED("FILE-004", "File upload failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}