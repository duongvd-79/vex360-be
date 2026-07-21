package com.example.vex360.shared.exceptions;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // SYSTEM ERRORS
    UNCATCHED_EXCEPTION("SYS-001", "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau.",
            HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("SYS-002", "Mã lỗi không hợp lệ.", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("SYS-003", "Dữ liệu nhập vào không hợp lệ.", HttpStatus.UNPROCESSABLE_CONTENT),

    METHOD_NOT_ALLOWED("SYS-004", "Phương thức HTTP không được hỗ trợ.", HttpStatus.METHOD_NOT_ALLOWED),
    REGISTRATION_ALREADY_EXISTS("REGISTRATION-002",
            "Doanh nghiệp đã có một lượt đăng ký đang hoạt động cho sự kiện này.",
            HttpStatus.CONFLICT),

    // AUTH ERRORS
    UNAUTHENTICATED("AUTH-001", "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.",
            HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("AUTH-002", "Bạn không có quyền thực hiện thao tác này.", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS("AUTH-003", "Email hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH-004", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.",
            HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("AUTH-005", "Tài khoản của bạn đã bị vô hiệu hóa.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_PENDING("AUTH-006", "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email để hoàn tất kích hoạt.",
            HttpStatus.UNAUTHORIZED),

    // BUSINESS ERRORS
    USER_NOT_FOUND("USER-001", "Không tìm thấy thông tin người dùng.", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "Email này đã được sử dụng.", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("USER-003", "Không tìm thấy vai trò người dùng.", HttpStatus.NOT_FOUND),
    FILE_SIZE_EXCEEDED("FILE-001", "Kích thước tệp vượt quá dung lượng cho phép (tối đa 10MB).",
            HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_SUPPORTED("FILE-002", "Định dạng tệp không được hỗ trợ.", HttpStatus.BAD_REQUEST),
    NOT_A_PANORAMA("FILE-003", "Tệp tải lên không phải là ảnh panorama 360 hợp lệ.", HttpStatus.BAD_REQUEST),
    UPLOAD_FAILED("FILE-004", "Tải tệp lên không thành công. Vui lòng thử lại.", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_TOO_LARGE("FILE-005", "Dung lượng tệp vượt quá giới hạn cho phép (tối đa 10MB).",
            HttpStatus.CONTENT_TOO_LARGE),
    STORAGE_QUOTA_EXCEEDED("FILE-006", "Dung lượng lưu trữ của doanh nghiệp đã đầy. Vui lòng nâng cấp gói lưu trữ.",
            HttpStatus.CONTENT_TOO_LARGE),
    STORAGE_PACKAGE_NOT_FOUND("STORAGE-001", "Không tìm thấy gói lưu trữ.", HttpStatus.NOT_FOUND),
    STORAGE_PACKAGE_ORDER_NOT_FOUND("STORAGE-002", "Không tìm thấy đơn hàng gói lưu trữ.", HttpStatus.NOT_FOUND),
    STORAGE_PACKAGE_NAME_DUPLICATED("STORAGE-003", "Tên gói lưu trữ đã tồn tại trong hệ thống.", HttpStatus.CONFLICT),
    INPUT_FAILED("USER-004", "Dữ liệu nhập vào không hợp lệ.", HttpStatus.BAD_REQUEST),
    OLDPASSWORD_FAILED("USER-005", "Mật khẩu hiện tại không chính xác.", HttpStatus.BAD_REQUEST),

    // PARTNERSHIP ERRORS
    PARTNERSHIP_EMAIL_ALREADY_REGISTERED("PARTNER-001",
            "Email này đã đăng ký tài khoản. Vui lòng đăng nhập để gửi yêu cầu hợp tác.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_NOT_FOUND("PARTNER-002", "Không tìm thấy thông tin yêu cầu hợp tác.", HttpStatus.NOT_FOUND),
    INVALID_PARTNERSHIP_ROLE("PARTNER-003",
            "Vai trò yêu cầu hợp tác phải là Nhà đăng ký gian hàng (Exhibitor) hoặc Nhà tổ chức (Organizer).",
            HttpStatus.BAD_REQUEST),
    INVALID_PARTNERSHIP_REQUEST_STATUS("PARTNER-004", "Trạng thái yêu cầu hợp tác không hợp lệ.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_ALREADY_PENDING("PARTNER-005",
            "Bạn đang có một yêu cầu hợp tác đang chờ duyệt. Vui lòng chờ quản trị viên xử lý.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUESTER_EMAIL_MUST_MATCH_AUTHENTICATED_USER("PARTNER-006",
            "Email liên hệ phải trùng với email tài khoản đang đăng nhập. Vui lòng đăng xuất nếu muốn gửi yêu cầu cho email khác.",
            HttpStatus.BAD_REQUEST),
    PARTNERSHIP_REQUEST_AWAITING_VERIFICATION("PARTNER-007",
            "Yêu cầu hợp tác đang chờ xác thực qua email. Vui lòng kiểm tra hộp thư của bạn.",
            HttpStatus.BAD_REQUEST),

    // COMPANY ERRORS
    COMPANY_NOT_FOUND("COMPANY-001", "Không tìm thấy thông tin doanh nghiệp.", HttpStatus.NOT_FOUND),
    COMPANY_PROFILE_INCOMPLETE("COMPANY-002", "Vui lòng hoàn thiện hồ sơ doanh nghiệp trước khi sử dụng tính năng này.",
            HttpStatus.FORBIDDEN),
    COMPANY_ARCHIVED("COMPANY-003", "Doanh nghiệp đã ngừng hoạt động, không thể thực hiện thao tác này.",
            HttpStatus.FORBIDDEN),

    // PRODUCT ERRORS
    PRODUCT_NOT_FOUND("PRODUCT-001", "Không tìm thấy thông tin sản phẩm.", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT-002", "Không tìm thấy danh mục sản phẩm.", HttpStatus.NOT_FOUND),
    PRODUCT_SKU_DUPLICATED("PRODUCT-003", "Mã sản phẩm (SKU) đã tồn tại trong hệ thống.", HttpStatus.CONFLICT),
    PRODUCT_CATEGORY_NAME_DUPLICATED("PRODUCT-004", "Tên danh mục sản phẩm đã tồn tại.", HttpStatus.CONFLICT),
    INVALID_PRODUCT_MEDIA("PRODUCT-005", "Hình ảnh hoặc video của sản phẩm không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_CATEGORY_STATUS("PRODUCT-006", "Trạng thái danh mục sản phẩm không hợp lệ.",
            HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_STATUS("PRODUCT-007", "Trạng thái sản phẩm không hợp lệ.", HttpStatus.BAD_REQUEST),
    PRODUCT_USED_BY_PENDING_BOOTH("PRODUCT-008",
            "Sản phẩm đang được sử dụng bởi một gian hàng chờ duyệt, không thể chỉnh sửa hoặc xóa.",
            HttpStatus.CONFLICT),

    // BOOTH ERRORS
    BOOTH_TEMPLATE_NOT_FOUND("BOOTH-001", "Không tìm thấy mẫu gian hàng (booth template).", HttpStatus.NOT_FOUND),
    INVALID_BOOTH_TEMPLATE("BOOTH-002", "Mẫu gian hàng không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_PANORAMA_HOTSPOT("BOOTH-003", "Điểm tương tác (hotspot) trên ảnh 360 không hợp lệ.",
            HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_INVALID("BOOTH-004", "Tệp ảnh panorama không hợp lệ.", HttpStatus.BAD_REQUEST),
    PANORAMA_FILE_SAVE_FAILED("BOOTH-005", "Không thể lưu tệp ảnh panorama. Vui lòng thử lại.",
            HttpStatus.INTERNAL_SERVER_ERROR),
    BOOTH_NOT_FOUND("BOOTH-006", "Không tìm thấy thông tin gian hàng.", HttpStatus.NOT_FOUND),
    INVALID_BOOTH("BOOTH-007", "Thông tin gian hàng không hợp lệ.", HttpStatus.BAD_REQUEST),
    PANORAMA_NOT_FOUND("BOOTH-008", "Không tìm thấy ảnh panorama 360.", HttpStatus.NOT_FOUND),
    HOTSPOT_NOT_FOUND("BOOTH-009", "Không tìm thấy điểm tương tác (hotspot).", HttpStatus.NOT_FOUND),
    INVALID_HOTSPOT("BOOTH-010", "Thông tin điểm tương tác (hotspot) không hợp lệ.", HttpStatus.BAD_REQUEST),
    MEDIA_ASSET_NOT_FOUND("BOOTH-011", "Không tìm thấy tệp phương tiện (hình ảnh/video).", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_ASSET("BOOTH-012", "Tệp phương tiện không hợp lệ.", HttpStatus.BAD_REQUEST),
    BOOTH_QUOTA_EXCEEDED("BOOTH-013", "Đã vượt quá số lượng gian hàng cho phép trong gói dịch vụ.",
            HttpStatus.BAD_REQUEST),
    BOOTH_TEMPLATE_NOT_COMPATIBLE("BOOTH-014", "Mẫu gian hàng không tương thích với gói đăng ký của bạn.",
            HttpStatus.BAD_REQUEST),
    BOOTH_NOT_EDITABLE("BOOTH-015", "Gian hàng không thể chỉnh sửa ở trạng thái hiện tại.", HttpStatus.BAD_REQUEST),
    INVALID_BOOTH_REVIEW_STATUS("BOOTH-016", "Trạng thái xét duyệt gian hàng không hợp lệ.", HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_DEADLINE_PASSED("BOOTH-017", "Đã quá hạn chót gửi yêu cầu xét duyệt gian hàng.",
            HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_ALREADY_PENDING("BOOTH-018", "Yêu cầu xét duyệt gian hàng đang được xử lý.", HttpStatus.BAD_REQUEST),
    BOOTH_REVIEW_REQUEST_NOT_FOUND("BOOTH-019", "Không tìm thấy yêu cầu xét duyệt gian hàng.", HttpStatus.NOT_FOUND),
    BOOTH_DRAFT_NOT_REVIEWABLE("BOOTH-020", "Bản thiết kế gian hàng chưa sẵn sàng để xét duyệt.", HttpStatus.FORBIDDEN),
    BOOTH_TEMPLATE_REQUIRES_EMPTY_BOOTH("BOOTH-021",
            "Gian hàng phải chưa có ảnh panorama trước khi áp dụng mẫu thiết kế.",
            HttpStatus.CONFLICT),

    // DESIGN REQUEST ERRORS
    DESIGN_REQUEST_NOT_FOUND("DESIGN-001", "Không tìm thấy yêu cầu thiết kế.", HttpStatus.NOT_FOUND),
    INVALID_DESIGN_REQUEST_STATUS("DESIGN-002", "Trạng thái yêu cầu thiết kế không hợp lệ.", HttpStatus.BAD_REQUEST),
    DESIGN_REQUEST_QUOTA_EXCEEDED("DESIGN-003", "Đã hết số lượt yêu cầu thiết kế cho phép cho gian hàng này.",
            HttpStatus.BAD_REQUEST),
    DESIGNER_WORKLOAD_EXCEEDED("DESIGN-004", "Nhà thiết kế đã đạt giới hạn tối đa số lượng công việc đang xử lý.",
            HttpStatus.BAD_REQUEST),
    INVALID_DESIGNER("DESIGN-005", "Người dùng được chỉ định không có vai trò Nhà thiết kế (Designer).",
            HttpStatus.BAD_REQUEST),
    INVALID_DESIGN_DRAFT("DESIGN-006", "Bản thiết kế nháp không hợp lệ.", HttpStatus.BAD_REQUEST),
    DESIGN_REQUEST_NOT_ELIGIBLE("DESIGN-007", "Gian hàng chưa đủ điều kiện cho yêu cầu thiết kế này.",
            HttpStatus.CONFLICT),
    DESIGN_PRODUCT_ACCESS_FORBIDDEN("DESIGN-008", "Không thể truy cập sản phẩm cho yêu cầu thiết kế này.",
            HttpStatus.FORBIDDEN),
    DESIGN_PRODUCT_NOT_ALLOWED("DESIGN-009", "Sản phẩm này không nằm trong danh sách cho phép thiết kế.",
            HttpStatus.FORBIDDEN),
    DESIGN_PRODUCT_LOCKED("DESIGN-010", "Sản phẩm đang được sử dụng ở một yêu cầu thiết kế khác.", HttpStatus.CONFLICT),
    DESIGN_CANCELLATION_PENDING("DESIGN-011", "Yêu cầu hủy thiết kế đang chờ quản trị viên phê duyệt.",
            HttpStatus.CONFLICT),
    DESIGN_MESSAGE_NOT_ALLOWED("DESIGN-012", "Bạn không có quyền tham gia luồng trao đổi này.", HttpStatus.FORBIDDEN),

    // PACKAGE TEMPLATE ERRORS
    PACKAGE_TEMPLATE_NOT_FOUND("PACKAGE-001", "Không tìm thấy mẫu gói dịch vụ.", HttpStatus.NOT_FOUND),
    PACKAGE_TEMPLATE_NAME_DUPLICATED("PACKAGE-002", "Tên mẫu gói dịch vụ đã tồn tại.", HttpStatus.CONFLICT),

    // EXHIBITION ERRORS
    EXHIBITION_NOT_FOUND("EXHIBITION-001", "Không tìm thấy thông tin triển lãm.", HttpStatus.NOT_FOUND),
    EXHIBITION_PACKAGE_NOT_FOUND("EXHIBITION-002", "Không tìm thấy gói triển lãm.", HttpStatus.NOT_FOUND),
    EXHIBITION_LIMIT_EXCEEDED("EXHIBITION-003",
            "Mỗi nhà tổ chức chỉ được có tối đa 3 đơn đăng ký ở trạng thái chờ duyệt đồng thời.",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_CANNOT_CANCEL("EXHIBITION-004", "Không thể hủy đơn đăng ký triển lãm ở trạng thái hiện tại.",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_INVALID_STATUS("EXHIBITION-005", "Trạng thái đơn đăng ký triển lãm không hợp lệ cho thao tác này.",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_ALREADY_REVIEWED("EXHIBITION-006", "Đơn đăng ký triển lãm đã được xử lý trước đó.",
            HttpStatus.BAD_REQUEST),
    EXHIBITION_NAME_DUPLICATED("EXHIBITION-007", "Tên triển lãm đã được sử dụng.", HttpStatus.CONFLICT),
    REGISTRATION_NOT_FOUND("REGISTRATION-001", "Không tìm thấy thông tin lượt đăng ký.", HttpStatus.NOT_FOUND),

    // CHAT ERRORS
    CHAT_ROOM_NOT_FOUND("CHAT-001", "Không tìm thấy phòng trò chuyện.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
