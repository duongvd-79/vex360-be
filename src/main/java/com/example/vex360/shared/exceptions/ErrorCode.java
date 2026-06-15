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

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "The email already exists", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND("USER-003", "Role not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}