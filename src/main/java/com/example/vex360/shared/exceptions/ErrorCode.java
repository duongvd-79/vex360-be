package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Uncaught system exception", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Invalid ErrorCode configuration", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),

    // AUTH ERRORS
    UNAUTHENTICATED("AUTH-001", "Unauthenticated or token expired", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("AUTH-002", "Unauthorized", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS("AUTH-003", "Invalid email or password", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED("AUTH-004", "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE("AUTH-005", "Account is inactive", HttpStatus.FORBIDDEN),
    ACCOUNT_EXPIRED("AUTH-006", "Account is expired", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("AUTH-007", "Account is disabled", HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_VERIFIED("AUTH-008", "Account is not verified", HttpStatus.FORBIDDEN),

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "The email already exists", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND("USER-003", "Role not found", HttpStatus.NOT_FOUND),
    INPUT_FAILED("USER-004", "Input failed", HttpStatus.BAD_REQUEST),
    OLDPASSWORD_FAILED("USER-005","Old Password is failed",HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}