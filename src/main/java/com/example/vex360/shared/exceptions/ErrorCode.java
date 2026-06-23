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
    OLDPASSWORD_FAILED("USER-005", "Old Password is failed", HttpStatus.BAD_REQUEST),

    // PARTNERSHIP ERRORS
    PARTNERSHIP_EMAIL_ALREADY_REGISTERED("PARTNER-001", "Email này đã có tài khoản. Vui lòng đăng nhập để gửi yêu cầu hợp tác.", HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_NOT_FOUND("PARTNER-002", "Partnership request not found", HttpStatus.NOT_FOUND),
    INVALID_PARTNERSHIP_ROLE("PARTNER-003", "Requested role must be EXHIBITOR or ORGANIZER", HttpStatus.BAD_REQUEST),
    INVALID_PARTNERSHIP_REQUEST_STATUS("PARTNER-004", "Partnership request status is invalid for this action", HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_ALREADY_PENDING("PARTNER-005", "Yêu cầu hợp tác đang chờ duyệt. Vui lòng chờ admin xử lý.", HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUESTER_EMAIL_MUST_MATCH_AUTHENTICATED_USER("PARTNER-006", "Email liên hệ phải trùng với email tài khoản đang đăng nhập. Vui lòng đăng xuất và gửi yêu cầu với tư cách guest.", HttpStatus.BAD_REQUEST),

    // COMPANY ERRORS
    COMPANY_NOT_FOUND("COMPANY-001", "Company not found", HttpStatus.NOT_FOUND),

    // BOOTH ERRORS
    BOOTH_TEMPLATE_NOT_FOUND("BOOTH-001", "Booth template not found", HttpStatus.NOT_FOUND),
    INVALID_BOOTH_TEMPLATE("BOOTH-002", "Invalid booth template", HttpStatus.BAD_REQUEST),
    INVALID_PANORAMA_HOTSPOT("BOOTH-003", "Invalid panorama hotspot", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_INVALID("BOOTH-004", "Invalid panorama file", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_SAVE_FAILED("BOOTH-005", "Cannot save panorama file", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
