package com.example.vex360.features.mail;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.utils.LogSanitizer;

import lombok.extern.slf4j.Slf4j;

@Service
@Async
@Slf4j
public class MailServiceImpl implements MailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailTransport emailTransport;
    private final String loginUrl;

    public MailServiceImpl(
            EmailTransport emailTransport,
            @Value("${app.registration.frontend-url}") String loginUrl) {
        this.emailTransport = emailTransport;
        this.loginUrl = loginUrl;
    }

    @Override
    public void sendForgotPasswordEmail(String toEmail, String resetUrl) {
        if (isEmailInvalid(toEmail)) return;
        String subject = "Yêu cầu khôi phục mật khẩu - VEX360";
        String safeResetUrl = HtmlUtils.htmlEscape(resetUrl);
        String htmlContent = buildHtmlTemplate(
                "Khôi phục mật khẩu",
                """
                        <h2>Yêu cầu khôi phục mật khẩu</h2>
                        <p>Xin chào,</p>
                        <p>Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản VEX360 của bạn. Vui lòng bấm vào nút bên dưới để tiến hành đổi mật khẩu mới (liên kết này có hiệu lực trong vòng 1 giờ):</p>
                        <div class="btn-container">
                            <a href="%1$s" class="btn">Khôi phục mật khẩu</a>
                        </div>
                        <p>Nếu nút trên không hoạt động, bạn có thể sao chép liên kết dưới đây và dán vào trình duyệt:</p>
                        <p style="word-break: break-all;"><a href="%1$s" style="color: #cc785c;">%1$s</a></p>
                        <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email này. Tài khoản của bạn vẫn được bảo mật.</p>"""
                        .formatted(safeResetUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendRegistrationVerificationEmail(String toEmail, String verifyUrl) {
        if (isEmailInvalid(toEmail)) return;
        String subject = "Xác thực tài khoản VEX360";
        String safeVerifyUrl = HtmlUtils.htmlEscape(verifyUrl);
        String htmlContent = buildHtmlTemplate(
                "Xác thực tài khoản",
                """
                        <h2>Xác thực tài khoản VEX360 của bạn</h2>
                        <p>Xin chào,</p>
                        <p>Cảm ơn bạn đã đăng ký tài khoản tại VEX360. Vui lòng bấm vào nút bên dưới để kích hoạt tài khoản và bắt đầu trải nghiệm dịch vụ của chúng tôi (liên kết này có hiệu lực trong vòng 24 giờ):</p>
                        <div class="btn-container">
                            <a href="%1$s" class="btn">Kích hoạt tài khoản</a>
                        </div>
                        <p>Nếu nút trên không hoạt động, bạn có thể sao chép liên kết dưới đây và dán vào trình duyệt:</p>
                        <p style="word-break: break-all;"><a href="%1$s" style="color: #cc785c;">%1$s</a></p>"""
                        .formatted(safeVerifyUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPasswordChangeNotificationEmail(String toEmail) {
        if (isEmailInvalid(toEmail)) return;
        String subject = "Mật khẩu của bạn đã được thay đổi thành công - VEX360";
        String htmlContent = buildHtmlTemplate(
                "Đổi mật khẩu thành công",
                """
                        <h2>Thay đổi mật khẩu thành công</h2>
                        <p>Xin chào,</p>
                        <p>Mật khẩu tài khoản VEX360 của bạn đã được thay đổi thành công.</p>
                        <div class="warning-box">
                            <p class="warning-text"><strong>CẢNH BÁO:</strong> Nếu bạn không thực hiện thay đổi này, tài khoản của bạn có thể đã bị xâm nhập. Vui lòng liên hệ với bộ phận hỗ trợ của chúng tôi ngay lập tức hoặc sử dụng chức năng Quên mật khẩu để lấy lại quyền kiểm soát tài khoản.</p>
                        </div>""");
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendNewUserCredentialsEmail(String toEmail, String fullName, String password) {
        if (isEmailInvalid(toEmail)) return;
        String subject = "Thông tin tài khoản VEX360";
        String displayName = safeDisplayName(fullName);
        String htmlContent = buildHtmlTemplate(
                "Thông tin tài khoản",
                """
                        <h2>Thông tin tài khoản của bạn</h2>
                        <p>Xin chào %s,</p>
                        <p>Tài khoản VEX360 của bạn đã được khởi tạo. Vui lòng sử dụng thông tin bên dưới để đăng nhập vào hệ thống:</p>
                        <div class="info-box">
                            <div class="label">Tài khoản</div>
                            <div class="value">%s</div>
                            <div class="label">Mật khẩu tạm thời</div>
                            <div class="value" style="margin-bottom: 0;">%s</div>
                        </div>
                        <p>Vì lý do bảo mật, vui lòng đổi mật khẩu sau khi đăng nhập thành công.</p>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(toEmail),
                                HtmlUtils.htmlEscape(password)));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPartnershipApprovedEmail(
            String toEmail,
            String fullName,
            Role role,
            String organizationName) {
        if (isEmailInvalid(toEmail)) return;
        String displayName = safeDisplayName(fullName);
        String subject = "Yêu cầu hợp tác đã được phê duyệt - VEX360";
        String htmlContent = buildHtmlTemplate(
                "Yêu cầu hợp tác đã được phê duyệt",
                """
                        <h2>Yêu cầu hợp tác đã được phê duyệt!</h2>
                        <p>Xin chào %s,</p>
                        <p>Chúc mừng bạn! Yêu cầu hợp tác cho tổ chức <strong>%s</strong> đã được phê duyệt thành công.</p>
                        <div class="info-box">
                            <div class="label">Tên tổ chức</div>
                            <div class="value">%s</div>
                            <div class="label">Vai trò được cấp</div>
                            <div class="value" style="margin-bottom: 0;">%s</div>
                        </div>
                        <p>Vui lòng đăng nhập vào hệ thống VEX360 để hoàn thiện hồ sơ công ty và bắt đầu sử dụng dịch vụ dành riêng cho đối tác.</p>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(organizationName),
                                HtmlUtils.htmlEscape(organizationName),
                                role.name()));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPartnershipRejectedEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String reviewNote) {
        if (isEmailInvalid(toEmail)) return;
        String displayName = safeDisplayName(fullName);
        String reason = reviewNote == null || reviewNote.isBlank()
                ? "Ban quản trị chưa cung cấp lý do cụ thể."
                : reviewNote;
        String subject = "Yêu cầu hợp tác chưa được phê duyệt - VEX360";
        String htmlContent = buildHtmlTemplate(
                "Yêu cầu hợp tác chưa được phê duyệt",
                """
                        <h2>Yêu cầu hợp tác chưa được phê duyệt</h2>
                        <p>Xin chào %s,</p>
                        <p>Cảm ơn bạn đã quan tâm và gửi yêu cầu hợp tác cho tổ chức <strong>%s</strong> trên hệ thống VEX360.</p>
                        <p>Rất tiếc, sau khi xem xét kỹ lưỡng, chúng tôi chưa thể phê duyệt yêu cầu hợp tác của bạn vào lúc này.</p>
                        <div class="reason-box">
                            <p class="reason-title">Lý do từ chối:</p>
                            <p class="reason-text">%s</p>
                        </div>
                        <p>Bạn có thể điều chỉnh thông tin cần thiết và thực hiện gửi lại yêu cầu hợp tác sau.</p>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(organizationName),
                                HtmlUtils.htmlEscape(reason)));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPartnershipVerificationEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String confirmUrl) {
        if (isEmailInvalid(toEmail)) return;
        String displayName = safeDisplayName(fullName);
        String safeConfirmUrl = HtmlUtils.htmlEscape(confirmUrl);
        String subject = "Xác nhận yêu cầu hợp tác - Vex360";
        String htmlContent = buildHtmlTemplate(
                "Xác minh yêu cầu hợp tác",
                """
                        <h2>Xác nhận yêu cầu hợp tác</h2>
                        <p>Xin chào %s,</p>
                        <p>Chúng tôi nhận được yêu cầu hợp tác cho tổ chức <strong>%s</strong> của bạn trên hệ thống VEX360. Vui lòng xác thực yêu cầu này bằng cách lựa chọn hành động bên dưới (liên kết này có hiệu lực trong vòng 24 giờ):</p>
                        <div class="btn-group">
                            <a href="%s" class="btn-confirm">Xác nhận gửi yêu cầu</a>
                        </div>
                        <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.</p>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(organizationName),
                                safeConfirmUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPartnershipGuestApprovedEmail(
            String toEmail,
            String fullName,
            String temporaryPassword,
            Role role,
            String organizationName) {
        if (isEmailInvalid(toEmail)) return;
        String displayName = safeDisplayName(fullName);
        String subject = "Yêu cầu hợp tác đã được phê duyệt - Thông tin tài khoản VEX360";
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String htmlContent = buildHtmlTemplate(
                "Yêu cầu hợp tác đã được phê duyệt",
                """
                        <h2>Yêu cầu hợp tác đã được phê duyệt!</h2>
                        <p>Xin chào %s,</p>
                        <p>Chúc mừng bạn! Yêu cầu hợp tác cho tổ chức <strong>%s</strong> đã được phê duyệt thành công. Tài khoản VEX360 của bạn đã được tự động khởi tạo.</p>
                        <div class="info-box">
                            <div class="label">Tên tổ chức</div>
                            <div class="value">%s</div>
                            <div class="label">Vai trò được cấp</div>
                            <div class="value">%s</div>
                            <div class="label">Email đăng nhập</div>
                            <div class="value">%s</div>
                            <div class="label">Mật khẩu tạm thời</div>
                            <div class="value" style="margin-bottom: 0;">%s</div>
                        </div>
                        <p>Vì lý do bảo mật, vui lòng đổi mật khẩu sau lần đăng nhập đầu tiên.</p>
                        <div class="btn-container">
                            <a href="%s" class="btn">Đăng nhập VEX360</a>
                        </div>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(organizationName),
                                HtmlUtils.htmlEscape(organizationName),
                                role.name(),
                                HtmlUtils.htmlEscape(toEmail),
                                HtmlUtils.htmlEscape(temporaryPassword),
                                safeLoginUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendExhibitionReviewResultEmail(
            String toEmail,
            String fullName,
            String exhibitionName,
            LocalDate startDate,
            LocalDate endDate,
            String resultStatus,
            String rejectedReason,
            int rejectionCount,
            Instant reviewedAt) {
        if (isEmailInvalid(toEmail)) return;
        boolean isApproved = "APPROVED".equalsIgnoreCase(resultStatus);
        boolean isRejected = "REJECTED".equalsIgnoreCase(resultStatus);
        if (!isApproved && !isRejected) {
            log.warn("Invalid ExhibitionReviewStatus for email sending: {}", resultStatus);
            return;
        }

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String formattedDates = formatDate(startDate) + " - " + formatDate(endDate);
        String formattedReviewedAt = formatInstant(reviewedAt);

        if (isApproved) {
            String subject = "Triển lãm \"" + exhibitionName + "\" đã được phê duyệt - VEX360";
            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt triển lãm",
                    """
                            <h2>Triển lãm đã được phê duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Triển lãm <strong>%s</strong> của bạn đã được Ban quản trị VEX360 phê duyệt thành công.</p>
                            <div class="info-box">
                                <div class="label">Tên triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Thời gian tổ chức</div>
                                <div class="value">%s</div>
                                <div class="label">Kết quả xét duyệt</div>
                                <div class="value">Đã phê duyệt</div>
                                <div class="label">Trạng thái mới</div>
                                <div class="value">%s</div>
                                <div class="label">Thời gian xét duyệt</div>
                                <div class="value" style="margin-bottom: 0;">%s</div>
                            </div>
                            <p>Bạn có thể đăng nhập vào hệ thống để hoàn thiện media, tạo các gói đăng ký gian hàng và theo dõi các doanh nghiệp đăng ký tham gia.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(formattedDates),
                                    "Đang mở đăng ký",
                                    HtmlUtils.htmlEscape(formattedReviewedAt),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        } else {
            String subject = "Triển lãm \"" + exhibitionName + "\" chưa được phê duyệt - VEX360";
            String safeReason = safeOptionalText(rejectedReason);
            String rejectionLimitGuidance = rejectionCount < 3
                    ? "<p>Vui lòng điều chỉnh thông tin cần thiết theo lý do trên và gửi lại yêu cầu xét duyệt.</p>"
                    : "<p>Triển lãm này đã đạt giới hạn tối đa 3 lần xét duyệt và không thể gửi lại.</p>";

            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt triển lãm",
                    """
                            <h2>Triển lãm chưa được phê duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Rất tiếc, triển lãm <strong>%s</strong> chưa được phê duyệt sau khi Ban quản trị xem xét.</p>
                            <div class="info-box">
                                <div class="label">Tên triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Thời gian dự kiến</div>
                                <div class="value">%s</div>
                                <div class="label">Kết quả xét duyệt</div>
                                <div class="value">Chưa được phê duyệt</div>
                                <div class="label">Số lần từ chối</div>
                                <div class="value" style="margin-bottom: 0;">%d/3</div>
                            </div>
                            <div class="reason-box">
                                <p class="reason-title">Lý do từ chối:</p>
                                <p class="reason-text">%s</p>
                            </div>
                            %s
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(formattedDates),
                                    rejectionCount,
                                    HtmlUtils.htmlEscape(safeReason),
                                    rejectionLimitGuidance,
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        }
    }

    @Override
    public void sendExhibitorRegistrationReviewResultEmail(
            String toEmail,
            String fullName,
            String companyName,
            String exhibitionName,
            String packageName,
            BigDecimal finalPrice,
            String currency,
            ExhibitorRegistrationStatus result,
            String rejectedReason) {
        if (isEmailInvalid(toEmail)) return;
        if (result != ExhibitorRegistrationStatus.APPROVED
                && result != ExhibitorRegistrationStatus.PENDING_PAYMENT
                && result != ExhibitorRegistrationStatus.REJECTED) {
            log.warn("Invalid ExhibitorRegistrationStatus for review email sending: {}", result);
            return;
        }

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String formattedPrice = formatMoney(finalPrice, currency);

        if (result == ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            String subject = "Đăng ký tham gia \"" + exhibitionName + "\" đã được duyệt - Vui lòng thanh toán";
            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt đăng ký",
                    """
                            <h2>Đăng ký tham gia triển lãm đã được duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Yêu cầu đăng ký tham gia triển lãm <strong>%s</strong> cho doanh nghiệp <strong>%s</strong> đã được Ban tổ chức chấp thuận.</p>
                            <div class="info-box">
                                <div class="label">Doanh nghiệp</div>
                                <div class="value">%s</div>
                                <div class="label">Triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Gói đăng ký</div>
                                <div class="value">%s</div>
                                <div class="label">Chi phí thanh toán</div>
                                <div class="value">%s</div>
                                <div class="label">Trạng thái</div>
                                <div class="value" style="margin-bottom: 0;">Chờ thanh toán</div>
                            </div>
                            <p>Vui lòng đăng nhập vào VEX360, truy cập chi tiết đăng ký để tiến hành thanh toán và hoàn tất thủ tục tham gia.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(packageName),
                                    HtmlUtils.htmlEscape(formattedPrice),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        } else if (result == ExhibitorRegistrationStatus.APPROVED) {
            boolean freeApproval = finalPrice != null && finalPrice.compareTo(BigDecimal.ZERO) == 0;
            String packageTag = freeApproval ? " (Gói miễn phí)" : "";
            String costDisplay = freeApproval ? "Miễn phí" : HtmlUtils.htmlEscape(formattedPrice);

            String subject = "Đăng ký tham gia \"" + exhibitionName + "\" đã được phê duyệt - VEX360";
            String htmlContent = buildHtmlTemplate(
                    "Xác nhận đăng ký tham gia triển lãm",
                    """
                            <h2>Đăng ký tham gia triển lãm thành công</h2>
                            <p>Xin chào %s,</p>
                            <p>Chúc mừng! Đăng ký tham gia triển lãm <strong>%s</strong> của doanh nghiệp <strong>%s</strong> đã được phê duyệt%s.</p>
                            <div class="info-box">
                                <div class="label">Doanh nghiệp</div>
                                <div class="value">%s</div>
                                <div class="label">Triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Gói đăng ký</div>
                                <div class="value">%s</div>
                                <div class="label">Chi phí</div>
                                <div class="value">%s</div>
                                <div class="label">Trạng thái</div>
                                <div class="value" style="margin-bottom: 0;">Đã phê duyệt</div>
                            </div>
                            <p>Gian hàng của bạn đã được tạo tự động. Hãy đăng nhập VEX360 để bắt đầu thiết lập gian hàng.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(companyName),
                                    packageTag,
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(packageName),
                                    costDisplay,
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        } else {
            String subject = "Đăng ký tham gia \"" + exhibitionName + "\" chưa được phê duyệt";
            String safeReason = safeOptionalText(rejectedReason);
            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt đăng ký",
                    """
                            <h2>Đăng ký tham gia chưa được phê duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Rất tiếc, đăng ký tham gia triển lãm <strong>%s</strong> của doanh nghiệp <strong>%s</strong> chưa được phê duyệt.</p>
                            <div class="info-box">
                                <div class="label">Doanh nghiệp</div>
                                <div class="value">%s</div>
                                <div class="label">Triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Gói đăng ký</div>
                                <div class="value" style="margin-bottom: 0;">%s</div>
                            </div>
                            <div class="reason-box">
                                <p class="reason-title">Lý do từ chối:</p>
                                <p class="reason-text">%s</p>
                            </div>
                            <p>Các liên kết thanh toán chờ xử lý (nếu có) đã bị hủy. Vui lòng kiểm tra lại điều kiện đăng ký trước khi thực hiện gửi lại.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(packageName),
                                    HtmlUtils.htmlEscape(safeReason),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        }
    }

    @Override
    public void sendExhibitorRegistrationPaymentConfirmedEmail(
            String toEmail,
            String fullName,
            String companyName,
            String exhibitionName,
            String packageName,
            BigDecimal amount,
            String currency,
            Long orderCode,
            Instant paidAt) {
        if (isEmailInvalid(toEmail)) return;

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String formattedAmount = formatMoney(amount, currency);
        String formattedPaidAt = formatInstant(paidAt);
        String orderCodeStr = orderCode != null ? String.valueOf(orderCode) : "Không có";

        String subject = "Thanh toán thành công - Đăng ký \"" + exhibitionName + "\" đã được xác nhận";
        String htmlContent = buildHtmlTemplate(
                "Xác nhận thanh toán thành công",
                """
                        <h2>Thanh toán thành công!</h2>
                        <p>Xin chào %s,</p>
                        <p>Giao dịch thanh toán cho đăng ký tham gia triển lãm <strong>%s</strong> của doanh nghiệp <strong>%s</strong> đã hoàn tất thành công.</p>
                        <div class="info-box">
                            <div class="label">Doanh nghiệp</div>
                            <div class="value">%s</div>
                            <div class="label">Triển lãm</div>
                            <div class="value">%s</div>
                            <div class="label">Gói đăng ký</div>
                            <div class="value">%s</div>
                            <div class="label">Số tiền thanh toán</div>
                            <div class="value">%s</div>
                            <div class="label">Mã giao dịch</div>
                            <div class="value">%s</div>
                            <div class="label">Thời gian thanh toán</div>
                            <div class="value">%s</div>
                            <div class="label">Trạng thái đăng ký</div>
                            <div class="value" style="margin-bottom: 0;">Đã phê duyệt</div>
                        </div>
                        <p>Gian hàng của bạn đã được khởi tạo và sẵn sàng thiết lập trên hệ thống VEX360.</p>
                        <div class="btn-container">
                            <a href="%s" class="btn">Đăng nhập VEX360</a>
                        </div>"""
                        .formatted(
                                HtmlUtils.htmlEscape(displayName),
                                HtmlUtils.htmlEscape(exhibitionName),
                                HtmlUtils.htmlEscape(companyName),
                                HtmlUtils.htmlEscape(companyName),
                                HtmlUtils.htmlEscape(exhibitionName),
                                HtmlUtils.htmlEscape(packageName),
                                HtmlUtils.htmlEscape(formattedAmount),
                                HtmlUtils.htmlEscape(orderCodeStr),
                                HtmlUtils.htmlEscape(formattedPaidAt),
                                safeLoginUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendBoothReviewResultEmail(
            String toEmail,
            String fullName,
            String boothName,
            String exhibitionName,
            Integer versionNumber,
            String resultStatus,
            String rejectedReason,
            Instant reviewedAt) {
        if (isEmailInvalid(toEmail)) return;
        boolean isApproved = "APPROVED".equalsIgnoreCase(resultStatus);
        boolean isRejected = "REJECTED".equalsIgnoreCase(resultStatus);
        if (!isApproved && !isRejected) {
            log.warn("Invalid BoothReviewStatus for email sending: {}", resultStatus);
            return;
        }

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String formattedReviewedAt = formatInstant(reviewedAt);
        String versionStr = versionNumber != null ? String.valueOf(versionNumber) : "1";

        if (isApproved) {
            String subject = "Gian hàng \"" + boothName + "\" đã được phê duyệt và xuất bản";
            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt gian hàng",
                    """
                            <h2>Gian hàng đã được phê duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Gian hàng <strong>%s</strong> thuộc triển lãm <strong>%s</strong> đã được phê duyệt và xuất bản thành công.</p>
                            <div class="info-box">
                                <div class="label">Gian hàng</div>
                                <div class="value">%s</div>
                                <div class="label">Triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Phiên bản xét duyệt</div>
                                <div class="value">v%s</div>
                                <div class="label">Trạng thái mới</div>
                                <div class="value">Đã xuất bản</div>
                                <div class="label">Thời gian xét duyệt</div>
                                <div class="value" style="margin-bottom: 0;">%s</div>
                            </div>
                            <p>Khách tham quan hiện tại đã có thể truy cập và trải nghiệm gian hàng của bạn.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(versionStr),
                                    HtmlUtils.htmlEscape(formattedReviewedAt),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        } else {
            String subject = "Gian hàng \"" + boothName + "\" cần được chỉnh sửa";
            String safeReason = safeOptionalText(rejectedReason);
            String htmlContent = buildHtmlTemplate(
                    "Kết quả xét duyệt gian hàng",
                    """
                            <h2>Gian hàng cần được chỉnh sửa</h2>
                            <p>Xin chào %s,</p>
                            <p>Yêu cầu phê duyệt cho gian hàng <strong>%s</strong> thuộc triển lãm <strong>%s</strong> chưa được chấp thuận.</p>
                            <div class="info-box">
                                <div class="label">Gian hàng</div>
                                <div class="value">%s</div>
                                <div class="label">Triển lãm</div>
                                <div class="value">%s</div>
                                <div class="label">Phiên bản xét duyệt</div>
                                <div class="value">v%s</div>
                                <div class="label">Trạng thái gian hàng</div>
                                <div class="value" style="margin-bottom: 0;">Bản nháp</div>
                            </div>
                            <div class="reason-box">
                                <p class="reason-title">Lý do từ chối:</p>
                                <p class="reason-text">%s</p>
                            </div>
                            <p>Vui lòng chỉnh sửa gian hàng theo phản hồi trên và gửi lại yêu cầu xét duyệt.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(exhibitionName),
                                    HtmlUtils.htmlEscape(versionStr),
                                    HtmlUtils.htmlEscape(safeReason),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        }
    }

    @Override
    public void sendDesignDraftReviewResultEmail(
            String toEmail,
            String fullName,
            String companyName,
            String boothName,
            Integer versionNumber,
            DesignRequestStatus result,
            String reviewNote) {
        if (isEmailInvalid(toEmail)) return;
        if (result != DesignRequestStatus.APPROVED && result != DesignRequestStatus.REVISION_REQUESTED) {
            log.warn("Invalid DesignRequestStatus for draft review email sending: {}", result);
            return;
        }

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String versionStr = versionNumber != null ? String.valueOf(versionNumber) : "1";

        if (result == DesignRequestStatus.APPROVED) {
            String subject = "Bản thiết kế cho gian hàng \"" + boothName + "\" đã được phê duyệt";
            String htmlContent = buildHtmlTemplate(
                    "Kết quả duyệt bản thiết kế",
                    """
                            <h2>Bản thiết kế đã được phê duyệt</h2>
                            <p>Xin chào %s,</p>
                            <p>Bản thiết kế cho gian hàng <strong>%s</strong> (Doanh nghiệp: <strong>%s</strong>) đã được phê duyệt thành công.</p>
                            <div class="info-box">
                                <div class="label">Doanh nghiệp</div>
                                <div class="value">%s</div>
                                <div class="label">Gian hàng</div>
                                <div class="value">%s</div>
                                <div class="label">Phiên bản thiết kế</div>
                                <div class="value">v%s</div>
                                <div class="label">Kết quả</div>
                                <div class="value" style="margin-bottom: 0;">Đã phê duyệt</div>
                            </div>
                            <p>Toàn bộ panorama, điểm tương tác (hotspot) và dữ liệu thiết kế đã được áp dụng trực tiếp vào gian hàng chính thức. Yêu cầu thiết kế hoàn thành.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(versionStr),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        } else {
            String subject = "Bản thiết kế cho gian hàng \"" + boothName + "\" cần được chỉnh sửa";
            String safeReason = safeOptionalText(reviewNote);
            String htmlContent = buildHtmlTemplate(
                    "Kết quả duyệt bản thiết kế",
                    """
                            <h2>Bản thiết kế cần được chỉnh sửa</h2>
                            <p>Xin chào %s,</p>
                            <p>Bản thiết kế phiên bản v%s cho gian hàng <strong>%s</strong> (Doanh nghiệp: <strong>%s</strong>) đã được phản hồi yêu cầu chỉnh sửa.</p>
                            <div class="info-box">
                                <div class="label">Doanh nghiệp</div>
                                <div class="value">%s</div>
                                <div class="label">Gian hàng</div>
                                <div class="value">%s</div>
                                <div class="label">Phiên bản bị từ chối</div>
                                <div class="value">v%s</div>
                                <div class="label">Trạng thái mới</div>
                                <div class="value" style="margin-bottom: 0;">Yêu cầu chỉnh sửa</div>
                            </div>
                            <div class="reason-box">
                                <p class="reason-title">Ghi chú góp ý:</p>
                                <p class="reason-text">%s</p>
                            </div>
                            <p>Bản nháp làm việc (working draft) đã được sao chép từ phiên bản này. Vui lòng kiểm tra góp ý và cập nhật lại bản thiết kế.</p>
                            <div class="btn-container">
                                <a href="%s" class="btn">Đăng nhập VEX360</a>
                            </div>"""
                            .formatted(
                                    HtmlUtils.htmlEscape(displayName),
                                    HtmlUtils.htmlEscape(versionStr),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(companyName),
                                    HtmlUtils.htmlEscape(boothName),
                                    HtmlUtils.htmlEscape(versionStr),
                                    HtmlUtils.htmlEscape(safeReason),
                                    safeLoginUrl));
            sendHtmlMail(toEmail, subject, htmlContent);
        }
    }

    @Override
    public void sendDesignCancellationDecisionEmail(
            String toEmail,
            String fullName,
            boolean designerRecipient,
            String companyName,
            String boothName,
            String resultStatus,
            String cancellationReason,
            String resolutionNote,
            Instant resolvedAt) {
        if (isEmailInvalid(toEmail)) return;
        boolean isApproved = "APPROVED".equalsIgnoreCase(resultStatus);
        boolean isRejected = "REJECTED".equalsIgnoreCase(resultStatus);
        if (!isApproved && !isRejected) {
            log.warn("Invalid DesignRequestCancellationStatus for decision email sending: {}", resultStatus);
            return;
        }

        String displayName = safeDisplayName(fullName);
        String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String safeReason = safeOptionalText(cancellationReason);
        String safeResolution = safeOptionalText(resolutionNote);

        if (designerRecipient) {
            if (isApproved) {
                String subject = "Yêu cầu thiết kế gian hàng \"" + boothName + "\" đã bị hủy";
                String htmlContent = buildHtmlTemplate(
                        "Thông báo xử lý yêu cầu hủy thiết kế",
                        """
                                <h2>Yêu cầu thiết kế đã bị hủy</h2>
                                <p>Xin chào %s,</p>
                                <p>Yêu cầu thiết kế cho gian hàng <strong>%s</strong> (Doanh nghiệp: <strong>%s</strong>) đã được chấp thuận hủy bởi Ban quản trị.</p>
                                <div class="info-box">
                                    <div class="label">Doanh nghiệp</div>
                                    <div class="value">%s</div>
                                    <div class="label">Gian hàng</div>
                                    <div class="value">%s</div>
                                    <div class="label">Quyết định Admin</div>
                                    <div class="value" style="margin-bottom: 0;">Đã chấp thuận hủy</div>
                                </div>
                                <div class="reason-box">
                                    <p class="reason-title">Ghi chú xử lý:</p>
                                    <p class="reason-text">%s</p>
                                </div>
                                <p>Bạn có thể dừng thực hiện yêu cầu thiết kế này trên hệ thống.</p>
                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập VEX360</a>
                                </div>"""
                                .formatted(
                                        HtmlUtils.htmlEscape(displayName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(companyName),
                                        HtmlUtils.htmlEscape(companyName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(safeResolution),
                                        safeLoginUrl));
                sendHtmlMail(toEmail, subject, htmlContent);
            } else {
                String subject = "Tiếp tục thực hiện thiết kế gian hàng \"" + boothName + "\"";
                String htmlContent = buildHtmlTemplate(
                        "Thông báo xử lý yêu cầu hủy thiết kế",
                        """
                                <h2>Tiếp tục thực hiện thiết kế</h2>
                                <p>Xin chào %s,</p>
                                <p>Yêu cầu hủy thiết kế cho gian hàng <strong>%s</strong> (Doanh nghiệp: <strong>%s</strong>) chưa được chấp thuận. Vui lòng tiếp tục công việc thiết kế.</p>
                                <div class="info-box">
                                    <div class="label">Doanh nghiệp</div>
                                    <div class="value">%s</div>
                                    <div class="label">Gian hàng</div>
                                    <div class="value">%s</div>
                                    <div class="label">Quyết định Admin</div>
                                    <div class="value" style="margin-bottom: 0;">Từ chối hủy (Tiếp tục)</div>
                                </div>
                                <div class="reason-box">
                                    <p class="reason-title">Ghi chú xử lý:</p>
                                    <p class="reason-text">%s</p>
                                </div>
                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập VEX360</a>
                                </div>"""
                                .formatted(
                                        HtmlUtils.htmlEscape(displayName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(companyName),
                                        HtmlUtils.htmlEscape(companyName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(safeResolution),
                                        safeLoginUrl));
                sendHtmlMail(toEmail, subject, htmlContent);
            }
        } else {
            if (isApproved) {
                String subject = "Yêu cầu hủy thiết kế gian hàng \"" + boothName + "\" đã được chấp thuận";
                String htmlContent = buildHtmlTemplate(
                        "Thông báo kết quả yêu cầu hủy thiết kế",
                        """
                                <h2>Yêu cầu hủy thiết kế đã được chấp thuận</h2>
                                <p>Xin chào %s,</p>
                                <p>Yêu cầu hủy thiết kế cho gian hàng <strong>%s</strong> đã được Ban quản trị chấp thuận.</p>
                                <div class="info-box">
                                    <div class="label">Gian hàng</div>
                                    <div class="value">%s</div>
                                    <div class="label">Trạng thái yêu cầu</div>
                                    <div class="value" style="margin-bottom: 0;">Đã hủy</div>
                                </div>
                                <div class="reason-box">
                                    <p class="reason-title">Lý do yêu cầu hủy:</p>
                                    <p class="reason-text">%s</p>
                                    <p class="reason-title" style="margin-top: 12px;">Ghi chú xử lý:</p>
                                    <p class="reason-text">%s</p>
                                </div>
                                <p>Gian hàng của bạn đã được mở khóa và chuyển về bản nháp để bạn có thể tiếp tục chỉnh sửa.</p>
                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập VEX360</a>
                                </div>"""
                                .formatted(
                                        HtmlUtils.htmlEscape(displayName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(safeReason),
                                        HtmlUtils.htmlEscape(safeResolution),
                                        safeLoginUrl));
                sendHtmlMail(toEmail, subject, htmlContent);
            } else {
                String subject = "Yêu cầu hủy thiết kế gian hàng \"" + boothName + "\" chưa được chấp thuận";
                String htmlContent = buildHtmlTemplate(
                        "Thông báo kết quả yêu cầu hủy thiết kế",
                        """
                                <h2>Yêu cầu hủy thiết kế chưa được chấp thuận</h2>
                                <p>Xin chào %s,</p>
                                <p>Rất tiếc, yêu cầu hủy thiết kế cho gian hàng <strong>%s</strong> chưa được chấp thuận.</p>
                                <div class="info-box">
                                    <div class="label">Gian hàng</div>
                                    <div class="value">%s</div>
                                    <div class="label">Trạng thái</div>
                                    <div class="value" style="margin-bottom: 0;">Tiếp tục thực hiện</div>
                                </div>
                                <div class="reason-box">
                                    <p class="reason-title">Lý do yêu cầu hủy:</p>
                                    <p class="reason-text">%s</p>
                                    <p class="reason-title" style="margin-top: 12px;">Ghi chú quyết định:</p>
                                    <p class="reason-text">%s</p>
                                </div>
                                <p>Quy trình thiết kế gian hàng hiện tại và Designer đảm nhận sẽ tiếp tục được giữ nguyên.</p>
                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập VEX360</a>
                                </div>"""
                                .formatted(
                                        HtmlUtils.htmlEscape(displayName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(boothName),
                                        HtmlUtils.htmlEscape(safeReason),
                                        HtmlUtils.htmlEscape(safeResolution),
                                        safeLoginUrl));
                sendHtmlMail(toEmail, subject, htmlContent);
            }
        }
    }

    private boolean isEmailInvalid(String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Recipient email is null or blank. Skipping email sending.");
            return true;
        }
        return false;
    }

    private String safeDisplayName(String fullName) {
        return (fullName == null || fullName.isBlank()) ? "bạn" : fullName;
    }

    private String safeOptionalText(String text) {
        return (text == null || text.isBlank()) ? "Không có ghi chú bổ sung" : text;
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return DATE_FORMATTER.format(date);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DATE_TIME_FORMATTER.format(instant);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        String curr = (currency == null || currency.isBlank()) ? "VND" : currency;
        if (amount == null) return "Không xác định";
        return new DecimalFormat("#,##0").format(amount) + " " + curr;
    }

    private void sendHtmlMail(String toEmail, String subject, String htmlContent) {
        log.info("Sending HTML email - Subject: {}", subject);
        try {
            emailTransport.send(toEmail, subject, htmlContent);
            log.info("HTML email sent successfully");
        } catch (Exception e) {
            String errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("Failed to send HTML email: {}", LogSanitizer.sanitize(errorMessage));
        }
    }

    private String buildHtmlTemplate(String title, String bodyHtml) {
        String bodyStyle = """
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background-color: #faf9f5;
                    color: #3d3d3a;
                    margin: 0;
                    padding: 40px 20px;
                    -webkit-font-smoothing: antialiased;
                }
                .container {
                    max-width: 600px;
                    margin: 0 auto;
                    background-color: #efe9de;
                    border: 1px solid #e6dfd8;
                    border-radius: 12px;
                    padding: 32px;
                    box-shadow: 0 4px 20px rgba(20, 20, 19, 0.04);
                }
                .logo {
                    font-family: 'Inter', -apple-system, sans-serif;
                    font-size: 24px;
                    font-weight: 600;
                    letter-spacing: 0.5px;
                    color: #141413;
                }
                .logo-spike {
                    color: #cc785c;
                    margin-right: 6px;
                }
                h2 {
                    font-family: 'Cormorant Garamond', 'EB Garamond', 'Georgia', serif;
                    font-size: 26px;
                    font-weight: 400;
                    line-height: 1.25;
                    color: #141413;
                    margin-top: 0;
                    margin-bottom: 20px;
                    letter-spacing: -0.3px;
                }
                p {
                    font-size: 15px;
                    line-height: 1.6;
                    color: #3d3d3a;
                }
                .btn-container, .btn-group {
                    text-align: center;
                    margin: 32px 0;
                }
                .btn, .btn-confirm {
                    display: inline-block;
                    background-color: #cc785c;
                    color: #ffffff !important;
                    text-decoration: none;
                    padding: 12px 24px;
                    font-weight: 500;
                    border-radius: 8px;
                    font-size: 15px;
                    box-shadow: 0 2px 8px rgba(204, 120, 92, 0.2);
                }
                .info-box, .warning-box, .reason-box {
                    background-color: #f5f0e8;
                    border: 1px solid #e6dfd8;
                    border-radius: 8px;
                    padding: 16px;
                    margin: 24px 0;
                }
                .info-box .label {
                    color: #8e8b82;
                    font-size: 13px;
                    margin-bottom: 6px;
                }
                .info-box .value {
                    color: #141413;
                    font-size: 16px;
                    font-weight: 600;
                    word-break: break-all;
                    margin-bottom: 16px;
                }
                .warning-text {
                    color: #c64545;
                    font-size: 14px;
                    margin: 0;
                    line-height: 1.5;
                }
                .reason-box {
                    border-left: 4px solid #cc785c;
                }
                .reason-title {
                    font-weight: 600;
                    font-size: 14px;
                    color: #cc785c;
                    margin: 0 0 8px 0;
                }
                .reason-text {
                    font-size: 15px;
                    margin: 0;
                    color: #3d3d3a;
                    line-height: 1.5;
                }
                .footer {
                    margin-top: 40px;
                    border-top: 1px solid #e6dfd8;
                    padding-top: 20px;
                    font-size: 12px;
                    color: #8e8b82;
                    text-align: center;
                    line-height: 1.5;
                }
                """;

        String logoHtml = """
                <span class="logo"><span class="logo-spike">✦</span>VEX360</span>
                """;

        String footerText = "Đây là email tự động từ hệ thống VEX360. Vui lòng không phản hồi email này.";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>%s</title>
                    <style>
                        .header {
                            text-align: center;
                            margin-bottom: 32px;
                        }
                        %s
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            %s
                        </div>
                        %s
                        <div class="footer">
                            <p>%s</p>
                        </div>
                    </div>
                </body>
                </html>""".formatted(title, bodyStyle, logoHtml, bodyHtml, footerText);
    }
}
