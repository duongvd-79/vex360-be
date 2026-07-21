package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Lỗi cấu hình ErrorCode", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Lỗi validation", HttpStatus.UNPROCESSABLE_CONTENT),

    METHOD_NOT_ALLOWED("SYS-004", "Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),
    REGISTRATION_ALREADY_EXISTS("REGISTRATION-002", "Nhà triển lãm đã có một lượt đăng ký hoạt động cho sự kiện này",
            HttpStatus.CONFLICT),

    // AUTH ERRORS
    UNAUTHENTICATED("AUTH-001", "Lỗi xác thực hoặc token hết hạn", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("AUTH-002", "Không có quyền truy cập", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS("AUTH-003", "Sai tài khoản hoặc mật khẩu", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH-004", "Tài khoản đã bị khóa", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("AUTH-005", "Tài khoản bị vô hiệu hóa", HttpStatus.UNAUTHORIZED),
    ACCOUNT_PENDING("AUTH-006", "Tài khoản chưa được kích hoạt", HttpStatus.UNAUTHORIZED),

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "Không tìm thấy user", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "Email đã tồn tại", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("USER-003", "Không tìm thấy role", HttpStatus.NOT_FOUND),
    FILE_SIZE_EXCEEDED("FILE-001", "Kích thước file vượt quá dung lượng cho phép (tối đa 10MB)",
            HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_SUPPORTED("FILE-002", "Loại file không được hỗ trợ", HttpStatus.BAD_REQUEST),
    NOT_A_PANORAMA("FILE-003", "Không phải là panorama", HttpStatus.BAD_REQUEST),
    UPLOAD_FAILED("FILE-004", "File upload failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_TOO_LARGE("FILE-005", "File vượt quá dung lượng cho phép (tối đa 10MB)", HttpStatus.CONTENT_TOO_LARGE),
    STORAGE_QUOTA_EXCEEDED("FILE-006", "Dung lượng lưu trữ của công ty đã đầy. Vui lòng nâng cấp gói dịch vụ.",
            HttpStatus.CONTENT_TOO_LARGE),
    STORAGE_PACKAGE_NOT_FOUND("STORAGE-001", "Không tìm thấy gói dịch vụ lưu trữ.", HttpStatus.NOT_FOUND),
    STORAGE_PACKAGE_ORDER_NOT_FOUND("STORAGE-002", "Không tìm thấy đơn hàng gói lưu trữ.", HttpStatus.NOT_FOUND),
    STORAGE_PACKAGE_NAME_DUPLICATED("STORAGE-003", "Tên gói lưu trữ đã tồn tại.", HttpStatus.CONFLICT),
    INPUT_FAILED("USER-004", "Input failed", HttpStatus.BAD_REQUEST),
    OLDPASSWORD_FAILED("USER-005", "Old Password is failed", HttpStatus.BAD_REQUEST),

    // PARTNERSHIP ERRORS
    PARTNERSHIP_EMAIL_ALREADY_REGISTERED("PARTNER-001",
            "Email này đã có tài khoản. Vui lòng đăng nhập để gửi yêu cầu hợp tác.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_NOT_FOUND("PARTNER-002", "Không tìm thấy yêu cầu hợp tác", HttpStatus.NOT_FOUND),
    INVALID_PARTNERSHIP_ROLE("PARTNER-003", "Vai trò yêu cầu là Nhà Triển Lãm hoặc Nhà Tổ Chức",
            HttpStatus.BAD_REQUEST),
    INVALID_PARTNERSHIP_REQUEST_STATUS("PARTNER-004", "Trạng thái yêu cầu hợp tác không hợp lệ",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_ALREADY_PENDING("PARTNER-005", "Yêu cầu hợp tác đang chờ duyệt. Vui lòng chờ admin xử lý.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUESTER_EMAIL_MUST_MATCH_AUTHENTICATED_USER("PARTNER-006",
            "Email liên hệ phải trùng với email tài khoản đang đăng nhập. Vui lòng đăng xuất và gửi yêu cầu với tư cách guest.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_AWAITING_VERIFICATION("PARTNER-007",
            "Yêu cầu hợp tác đang chờ xác thực qua email. Vui lòng kiểm tra hộp thư của bạn.",
            HttpStatus.BAD_REQUEST),

    // COMPANY ERRORS
    COMPANY_NOT_FOUND("COMPANY-001", "Không tìm thấy công ty", HttpStatus.NOT_FOUND),
    COMPANY_PROFILE_INCOMPLETE("COMPANY-002", "Hồ sơ công ty phải được hoàn thành trước khi sử dụng tính năng này",
            HttpStatus.FORBIDDEN),
    COMPANY_ARCHIVED("COMPANY-003", "Công ty đã lưu trữ không thể sử dụng tính năng này", HttpStatus.FORBIDDEN),

    // PRODUCT ERRORS
    PRODUCT_NOT_FOUND("PRODUCT-001", "Không tìm thấy sản phẩm", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT-002", "Không tìm thấy danh mục sản phẩm", HttpStatus.NOT_FOUND),
    PRODUCT_SKU_DUPLICATED("PRODUCT-003", "Mã sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_NAME_DUPLICATED("PRODUCT-004", "Tên danh mục sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    INVALID_PRODUCT_MEDIA("PRODUCT-005", "Hình ảnh hoặc video sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_CATEGORY_STATUS("PRODUCT-006", "Trạng thái danh mục sản phẩm không hợp lệ",
            HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_STATUS("PRODUCT-007", "Trạng thái sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),
    PRODUCT_USED_BY_PENDING_BOOTH("PRODUCT-008", "Sản phẩm đang được sử dụng bởi một booth chờ duyệt",
            HttpStatus.CONFLICT),

    // BOOTH ERRORS
    BOOTH_TEMPLATE_NOT_FOUND("BOOTH-001", "Không tìm thấy template booth", HttpStatus.NOT_FOUND),
    INVALID_BOOTH_TEMPLATE("BOOTH-002", "Template booth không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PANORAMA_HOTSPOT("BOOTH-003", "Hotspot panorama không hợp lệ", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_INVALID("BOOTH-004", "File panorama không hợp lệ", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_SAVE_FAILED("BOOTH-005", "Không thể lưu file panorama", HttpStatus.INTERNAL_SERVER_ERROR),
    BOOTH_NOT_FOUND("BOOTH-006", "Không tìm thấy booth", HttpStatus.NOT_FOUND),
    INVALID_BOOTH("BOOTH-007", "Booth không hợp lệ", HttpStatus.BAD_REQUEST),
    PANORAMA_NOT_FOUND("BOOTH-008", "Không tìm thấy panorama", HttpStatus.NOT_FOUND),
    HOTSPOT_NOT_FOUND("BOOTH-009", "Không tìm thấy hotspot", HttpStatus.NOT_FOUND),
    INVALID_HOTSPOT("BOOTH-010", "Hotspot không hợp lệ", HttpStatus.BAD_REQUEST),
    MEDIA_ASSET_NOT_FOUND("BOOTH-011", "Không tìm thấy media asset", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_ASSET("BOOTH-012", "Media asset không hợp lệ", HttpStatus.BAD_REQUEST),
    BOOTH_QUOTA_EXCEEDED("BOOTH-013", "Vượt quá hạn ngạch lợi ích gói booth", HttpStatus.BAD_REQUEST),
    BOOTH_TEMPLATE_NOT_COMPATIBLE("BOOTH-014", "Template booth không tương thích với gói booth",
            HttpStatus.BAD_REQUEST),
    BOOTH_NOT_EDITABLE("BOOTH-015", "Booth không thể chỉnh sửa trong trạng thái hiện tại", HttpStatus.BAD_REQUEST),
    INVALID_BOOTH_REVIEW_STATUS("BOOTH-016", "Trạng thái review booth không hợp lệ", HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_DEADLINE_PASSED("BOOTH-017", "Hạn chót review booth đã trôi qua", HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_ALREADY_PENDING("BOOTH-018", "Yêu cầu review booth đang chờ xử lý", HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_REQUEST_NOT_FOUND("BOOTH-019", "Không tìm thấy yêu cầu review booth", HttpStatus.NOT_FOUND),
    BOOTH_DRAFT_NOT_REVIEWABLE("BOOTH-020", "Nội dung bản thảo booth không có sẵn để review", HttpStatus.FORBIDDEN),
    BOOTH_TEMPLATE_REQUIRES_EMPTY_BOOTH("BOOTH-021", "Booth phải không có panorama trước khi áp dụng template",
            HttpStatus.CONFLICT),

    // DESIGN REQUEST ERRORS
    DESIGN_REQUEST_NOT_FOUND("DESIGN-001", "Không tìm thấy yêu cầu thiết kế", HttpStatus.NOT_FOUND),
    INVALID_DESIGN_REQUEST_STATUS("DESIGN-002", "Trạng thái yêu cầu thiết kế không hợp lệ", HttpStatus.BAD_REQUEST),
    DESIGN_REQUEST_QUOTA_EXCEEDED("DESIGN-003", "Vượt quá hạn ngạch yêu cầu thiết kế booth", HttpStatus.BAD_REQUEST),
    DESIGNER_WORKLOAD_EXCEEDED("DESIGN-004", "Vượt quá tải công việc thiết kế", HttpStatus.BAD_REQUEST),
    INVALID_DESIGNER("DESIGN-005", "Người dùng được chỉ định phải là nhà thiết kế", HttpStatus.BAD_REQUEST),
    INVALID_DESIGN_DRAFT("DESIGN-006", "Bản thảo thiết kế không hợp lệ", HttpStatus.BAD_REQUEST),
    DESIGN_REQUEST_NOT_ELIGIBLE("DESIGN-007", "Booth không đủ điều kiện cho yêu cầu thiết kế này",
            HttpStatus.CONFLICT),
    DESIGN_PRODUCT_ACCESS_FORBIDDEN("DESIGN-008", "Sản phẩm không có sẵn cho yêu cầu thiết kế này",
            HttpStatus.FORBIDDEN),
    DESIGN_PRODUCT_NOT_ALLOWED("DESIGN-009", "Sản phẩm không có trong danh sách cho phép thiết kế",
            HttpStatus.FORBIDDEN),
    DESIGN_PRODUCT_LOCKED("DESIGN-010", "Sản phẩm bị khóa bởi một yêu cầu thiết kế khác", HttpStatus.CONFLICT),
    DESIGN_CANCELLATION_PENDING("DESIGN-011", "Quyết định hủy đang chờ xử lý", HttpStatus.CONFLICT),
    DESIGN_MESSAGE_NOT_ALLOWED("DESIGN-012", "Không thể truy cập luồng thảo luận này", HttpStatus.FORBIDDEN),

    // PACKAGE TEMPLATE ERRORS
    PACKAGE_TEMPLATE_NOT_FOUND("PACKAGE-001", "Không tìm thấy package template", HttpStatus.NOT_FOUND),
    PACKAGE_TEMPLATE_NAME_DUPLICATED("PACKAGE-002", "Tên package template đã tồn tại", HttpStatus.CONFLICT),

    // EXHIBITION ERRORS
    EXHIBITION_NOT_FOUND("EXHIBITION-001", "Không tìm thấy triển lãm", HttpStatus.NOT_FOUND),
    EXHIBITION_PACKAGE_NOT_FOUND("EXHIBITION-002", "Không tìm thấy gói triển lãm", HttpStatus.NOT_FOUND),
    EXHIBITION_LIMIT_EXCEEDED("EXHIBITION-003",
            "Mỗi nhà tổ chức chỉ được có tối đa 3 đơn đăng ký ở trạng thái PENDING đồng thời",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_CANNOT_CANCEL("EXHIBITION-004", "Không được quyền huỷ đơn đăng ký triển lãm sau khi đăng ký",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_INVALID_STATUS("EXHIBITION-005", "Trạng thái đơn đăng ký triển lãm không hợp lệ cho thao tác này",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_ALREADY_REVIEWED("EXHIBITION-006", "Đơn đăng ký triển lãm đã được duyệt hoặc từ chối trước đó",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_NAME_DUPLICATED("EXHIBITION-007", "Tên triển lãm đã tồn tại", HttpStatus.CONFLICT),
    REGISTRATION_NOT_FOUND("REGISTRATION-001", "Không tìm thấy lượt đăng ký", HttpStatus.NOT_FOUND),

    // CHAT ERRORS
    CHAT_ROOM_NOT_FOUND("CHAT-001", "Không tìm thấy phòng chat", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
