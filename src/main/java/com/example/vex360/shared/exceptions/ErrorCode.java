package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Lỗi cấu hình ErrorCode", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Lỗi validation", HttpStatus.UNPROCESSABLE_ENTITY),

    METHOD_NOT_ALLOWED("SYS-004", "HTTP method is not supported", HttpStatus.METHOD_NOT_ALLOWED),

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
    UPLOAD_FAILED("FILE-004", "File upload failed", HttpStatus.INTERNAL_SERVER_ERROR),
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

    // PRODUCT ERRORS
    PRODUCT_NOT_FOUND("PRODUCT-001", "Không tìm thấy sản phẩm", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT-002", "Không tìm thấy danh mục sản phẩm", HttpStatus.NOT_FOUND),
    PRODUCT_SKU_DUPLICATED("PRODUCT-003", "Mã sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_NAME_DUPLICATED("PRODUCT-004", "Tên danh mục sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    INVALID_PRODUCT_MEDIA("PRODUCT-005", "Hình ảnh hoặc video sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_CATEGORY_STATUS("PRODUCT-006", "Trạng thái danh mục sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),

    // BOOTH ERRORS
    BOOTH_TEMPLATE_NOT_FOUND("BOOTH-001", "Booth template not found", HttpStatus.NOT_FOUND),
    INVALID_BOOTH_TEMPLATE("BOOTH-002", "Invalid booth template", HttpStatus.BAD_REQUEST),
    INVALID_PANORAMA_HOTSPOT("BOOTH-003", "Invalid panorama hotspot", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_INVALID("BOOTH-004", "Invalid panorama file", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_SAVE_FAILED("BOOTH-005", "Cannot save panorama file", HttpStatus.INTERNAL_SERVER_ERROR),

    // PACKAGE TEMPLATE ERRORS
    PACKAGE_TEMPLATE_NOT_FOUND("PACKAGE-001", "Package template not found", HttpStatus.NOT_FOUND),
    PACKAGE_TEMPLATE_NAME_DUPLICATED("PACKAGE-002", "Package template name already exists", HttpStatus.CONFLICT),

    // EXHIBITION ERRORS
    EXHIBITION_NOT_FOUND("EXHIBITION-001", "Không tìm thấy triển lãm", HttpStatus.NOT_FOUND),
    EXHIBITION_PACKAGE_NOT_FOUND("EXHIBITION-002", "Không tìm thấy gói triển lãm", HttpStatus.NOT_FOUND),
    REGISTRATION_NOT_FOUND("REGISTRATION-001", "Không tìm thấy lượt đăng ký", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
