package com.example.vex360.features.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.vex360.shared.enums.Role;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Async
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendForgotPasswordEmail(String toEmail, String resetUrl) {
        String subject = "Yêu cầu khôi phục mật khẩu - VEX360";
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
                        .formatted(resetUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendRegistrationVerificationEmail(String toEmail, String verifyUrl) {
        String subject = "Xác thực tài khoản VEX360";
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
                        .formatted(verifyUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendPasswordChangeNotificationEmail(String toEmail) {
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
        String subject = "Thông tin tài khoản VEX360";
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
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
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
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
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
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
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
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
                                confirmUrl));
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    private void sendHtmlMail(String toEmail, String subject, String htmlContent) {
        log.info("Sending HTML email - To: {}, Subject: {}", toEmail, subject);
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email has been logged but not sent via SMTP.");
            log.info("Logged Email Body:\n{}", htmlContent);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("HTML email sent successfully via SMTP to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send HTML email via SMTP to {}: {}", toEmail, e.getMessage());
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
