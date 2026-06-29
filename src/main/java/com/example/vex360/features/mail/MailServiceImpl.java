package com.example.vex360.features.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.vex360.shared.enums.Role;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Async
    @Override
    public void sendForgotPasswordEmail(String toEmail, String resetUrl) {
        String subject = "Yêu cầu khôi phục mật khẩu - VEX360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Khôi phục mật khẩu</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
                +
                "            background-color: #faf9f5;\n" +
                "            color: #3d3d3a;\n" +
                "            margin: 0;\n" +
                "            padding: 40px 20px;\n" +
                "            -webkit-font-smoothing: antialiased;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 600px;\n" +
                "            margin: 0 auto;\n" +
                "            background-color: #efe9de;\n" +
                "            border: 1px solid #e6dfd8;\n" +
                "            border-radius: 12px;\n" +
                "            padding: 32px;\n" +
                "            box-shadow: 0 4px 20px rgba(20, 20, 19, 0.04);\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 32px;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            font-family: 'Inter', -apple-system, sans-serif;\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 600;\n" +
                "            letter-spacing: 0.5px;\n" +
                "            color: #141413;\n" +
                "        }\n" +
                "        .logo-spike {\n" +
                "            color: #cc785c;\n" +
                "            margin-right: 6px;\n" +
                "        }\n" +
                "        h2 {\n" +
                "            font-family: 'Cormorant Garamond', 'EB Garamond', 'Georgia', serif;\n" +
                "            font-size: 26px;\n" +
                "            font-weight: 400;\n" +
                "            line-height: 1.25;\n" +
                "            color: #141413;\n" +
                "            margin-top: 0;\n" +
                "            margin-bottom: 20px;\n" +
                "            letter-spacing: -0.3px;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 15px;\n" +
                "            line-height: 1.6;\n" +
                "            color: #3d3d3a;\n" +
                "        }\n" +
                "        .btn-container {\n" +
                "            text-align: center;\n" +
                "            margin: 32px 0;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            background-color: #cc785c;\n" +
                "            color: #ffffff !important;\n" +
                "            text-decoration: none;\n" +
                "            padding: 12px 24px;\n" +
                "            font-weight: 500;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 15px;\n" +
                "            box-shadow: 0 2px 8px rgba(204, 120, 92, 0.2);\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 40px;\n" +
                "            border-top: 1px solid #e6dfd8;\n" +
                "            padding-top: 20px;\n" +
                "            font-size: 12px;\n" +
                "            color: #8e8b82;\n" +
                "            text-align: center;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <span class=\"logo\"><span class=\"logo-spike\">✦</span>VEX360</span>\n" +
                "        </div>\n" +
                "        <h2>Yêu cầu khôi phục mật khẩu</h2>\n" +
                "        <p>Xin chào,</p>\n" +
                "        <p>Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản VEX360 của bạn. Vui lòng bấm vào nút bên dưới để tiến hành đổi mật khẩu mới (liên kết này có hiệu lực trong vòng 1 giờ):</p>\n"
                +
                "        <div class=\"btn-container\">\n" +
                "            <a href=\"" + resetUrl + "\" class=\"btn\">Khôi phục mật khẩu</a>\n" +
                "        </div>\n" +
                "        <p>Nếu nút trên không hoạt động, bạn có thể sao chép liên kết dưới đây và dán vào trình duyệt:</p>\n"
                +
                "        <p style=\"word-break: break-all;\"><a href=\"" + resetUrl + "\" style=\"color: #cc785c;\">"
                + resetUrl + "</a></p>\n" +
                "        <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email này. Tài khoản của bạn vẫn được bảo mật.</p>\n"
                +
                "        <div class=\"footer\">\n" +
                "            <p>Đây là email tự động từ hệ thống VEX360. Vui lòng không phản hồi email này.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Async
    @Override
    public void sendRegistrationVerificationEmail(String toEmail, String verifyUrl) {
        String subject = "Xác thực tài khoản VEX360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Xác thực tài khoản</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
                +
                "            background-color: #faf9f5;\n" +
                "            color: #3d3d3a;\n" +
                "            margin: 0;\n" +
                "            padding: 40px 20px;\n" +
                "            -webkit-font-smoothing: antialiased;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 600px;\n" +
                "            margin: 0 auto;\n" +
                "            background-color: #efe9de;\n" +
                "            border: 1px solid #e6dfd8;\n" +
                "            border-radius: 12px;\n" +
                "            padding: 32px;\n" +
                "            box-shadow: 0 4px 20px rgba(20, 20, 19, 0.04);\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 32px;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            font-family: 'Inter', -apple-system, sans-serif;\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 600;\n" +
                "            letter-spacing: 0.5px;\n" +
                "            color: #141413;\n" +
                "        }\n" +
                "        .logo-spike {\n" +
                "            color: #cc785c;\n" +
                "            margin-right: 6px;\n" +
                "        }\n" +
                "        h2 {\n" +
                "            font-family: 'Cormorant Garamond', 'EB Garamond', 'Georgia', serif;\n" +
                "            font-size: 26px;\n" +
                "            font-weight: 400;\n" +
                "            line-height: 1.25;\n" +
                "            color: #141413;\n" +
                "            margin-top: 0;\n" +
                "            margin-bottom: 20px;\n" +
                "            letter-spacing: -0.3px;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 15px;\n" +
                "            line-height: 1.6;\n" +
                "            color: #3d3d3a;\n" +
                "        }\n" +
                "        .btn-container {\n" +
                "            text-align: center;\n" +
                "            margin: 32px 0;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            background-color: #cc785c;\n" +
                "            color: #ffffff !important;\n" +
                "            text-decoration: none;\n" +
                "            padding: 12px 24px;\n" +
                "            font-weight: 500;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 15px;\n" +
                "            box-shadow: 0 2px 8px rgba(204, 120, 92, 0.2);\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 40px;\n" +
                "            border-top: 1px solid #e6dfd8;\n" +
                "            padding-top: 20px;\n" +
                "            font-size: 12px;\n" +
                "            color: #8e8b82;\n" +
                "            text-align: center;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <span class=\"logo\"><span class=\"logo-spike\">✦</span>VEX360</span>\n" +
                "        </div>\n" +
                "        <h2>Xác thực tài khoản VEX360 của bạn</h2>\n" +
                "        <p>Xin chào,</p>\n" +
                "        <p>Cảm ơn bạn đã đăng ký tài khoản tại VEX360. Vui lòng bấm vào nút bên dưới để kích hoạt tài khoản và bắt đầu trải nghiệm dịch vụ của chúng tôi (liên kết này có hiệu lực trong vòng 24 giờ):</p>\n"
                +
                "        <div class=\"btn-container\">\n" +
                "            <a href=\"" + verifyUrl + "\" class=\"btn\">Kích hoạt tài khoản</a>\n" +
                "        </div>\n" +
                "        <p>Nếu nút trên không hoạt động, bạn có thể sao chép liên kết dưới đây và dán vào trình duyệt:</p>\n"
                +
                "        <p style=\"word-break: break-all;\"><a href=\"" + verifyUrl + "\" style=\"color: #cc785c;\">"
                + verifyUrl + "</a></p>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>Đây là email tự động từ hệ thống VEX360. Vui lòng không phản hồi email này.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Async
    @Override
    public void sendPasswordChangeNotificationEmail(String toEmail) {
        String subject = "Mật khẩu của bạn đã được thay đổi thành công";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Đổi mật khẩu thành công</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
                +
                "            background-color: #faf9f5;\n" +
                "            color: #3d3d3a;\n" +
                "            margin: 0;\n" +
                "            padding: 40px 20px;\n" +
                "            -webkit-font-smoothing: antialiased;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 600px;\n" +
                "            margin: 0 auto;\n" +
                "            background-color: #efe9de;\n" +
                "            border: 1px solid #e6dfd8;\n" +
                "            border-radius: 12px;\n" +
                "            padding: 32px;\n" +
                "            box-shadow: 0 4px 20px rgba(20, 20, 19, 0.04);\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 32px;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            font-family: 'Inter', -apple-system, sans-serif;\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 600;\n" +
                "            letter-spacing: 0.5px;\n" +
                "            color: #141413;\n" +
                "        }\n" +
                "        .logo-spike {\n" +
                "            color: #cc785c;\n" +
                "            margin-right: 6px;\n" +
                "        }\n" +
                "        h2 {\n" +
                "            font-family: 'Cormorant Garamond', 'EB Garamond', 'Georgia', serif;\n" +
                "            font-size: 26px;\n" +
                "            font-weight: 400;\n" +
                "            line-height: 1.25;\n" +
                "            color: #141413;\n" +
                "            margin-top: 0;\n" +
                "            margin-bottom: 20px;\n" +
                "            letter-spacing: -0.3px;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 15px;\n" +
                "            line-height: 1.6;\n" +
                "            color: #3d3d3a;\n" +
                "        }\n" +
                "        .warning-box {\n" +
                "            background-color: #f5f0e8;\n" +
                "            border: 1px solid #e6dfd8;\n" +
                "            border-radius: 8px;\n" +
                "            padding: 16px;\n" +
                "            margin: 24px 0;\n" +
                "        }\n" +
                "        .warning-text {\n" +
                "            color: #c64545;\n" +
                "            font-size: 14px;\n" +
                "            margin: 0;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 40px;\n" +
                "            border-top: 1px solid #e6dfd8;\n" +
                "            padding-top: 20px;\n" +
                "            font-size: 12px;\n" +
                "            color: #8e8b82;\n" +
                "            text-align: center;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <span class=\"logo\"><span class=\"logo-spike\">✦</span>VEX360</span>\n" +
                "        </div>\n" +
                "        <h2>Thay đổi mật khẩu thành công</h2>\n" +
                "        <p>Xin chào,</p>\n" +
                "        <p>Mật khẩu tài khoản VEX360 của bạn đã được thay đổi thành công.</p>\n" +
                "        <div class=\"warning-box\">\n" +
                "            <p class=\"warning-text\"><strong>CẢNH BÁO:</strong> Nếu bạn không thực hiện thay đổi này, tài khoản của bạn có thể đã bị xâm nhập. Vui lòng liên hệ với bộ phận hỗ trợ của chúng tôi ngay lập tức hoặc sử dụng chức năng Quên mật khẩu để lấy lại quyền kiểm soát tài khoản.</p>\n"
                +
                "        </div>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>Đây là email tự động từ hệ thống VEX360. Vui lòng không phản hồi email này.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Async
    @Override
    public void sendNewUserCredentialsEmail(String toEmail, String fullName, String password) {
        String subject = "Thông tin tài khoản Vex360";
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Thông tin tài khoản</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Outfit', 'Inter', sans-serif;\n" +
                "            background-color: #0d0e12;\n" +
                "            color: #e2e8f0;\n" +
                "            margin: 0;\n" +
                "            padding: 40px 20px;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 600px;\n" +
                "            margin: 0 auto;\n" +
                "            background: #1e2028;\n" +
                "            border: 1px solid rgba(255, 255, 255, 0.08);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 32px;\n" +
                "            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 32px;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            font-size: 28px;\n" +
                "            font-weight: 800;\n" +
                "            letter-spacing: -0.5px;\n" +
                "            background: linear-gradient(135deg, #a78bfa, #3b82f6);\n" +
                "            -webkit-background-clip: text;\n" +
                "            -webkit-text-fill-color: transparent;\n" +
                "            color: #3b82f6;\n" +
                "        }\n" +
                "        h2 {\n" +
                "            font-size: 22px;\n" +
                "            font-weight: 600;\n" +
                "            color: #ffffff;\n" +
                "            margin-top: 0;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 15px;\n" +
                "            line-height: 1.6;\n" +
                "            color: #94a3b8;\n" +
                "        }\n" +
                "        .credentials {\n" +
                "            background: rgba(15, 23, 42, 0.72);\n" +
                "            border: 1px solid rgba(148, 163, 184, 0.18);\n" +
                "            border-radius: 12px;\n" +
                "            padding: 20px;\n" +
                "            margin: 28px 0;\n" +
                "        }\n" +
                "        .label {\n" +
                "            color: #94a3b8;\n" +
                "            font-size: 13px;\n" +
                "            margin-bottom: 6px;\n" +
                "        }\n" +
                "        .value {\n" +
                "            color: #ffffff;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
                "            word-break: break-all;\n" +
                "            margin-bottom: 16px;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 40px;\n" +
                "            border-top: 1px solid rgba(255, 255, 255, 0.06);\n" +
                "            padding-top: 20px;\n" +
                "            font-size: 12px;\n" +
                "            color: #64748b;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <span class=\"logo\">VEX360</span>\n" +
                "        </div>\n" +
                "        <h2>Thông tin tài khoản của bạn</h2>\n" +
                "        <p>Xin chào " + escapeHtml(displayName) + ",</p>\n" +
                "        <p>Tài khoản Vex360 của bạn đã được khởi tạo. Vui lòng sử dụng thông tin bên dưới để đăng nhập vào hệ thống:</p>\n"
                +
                "        <div class=\"credentials\">\n" +
                "            <div class=\"label\">Tài khoản</div>\n" +
                "            <div class=\"value\">" + escapeHtml(toEmail) + "</div>\n" +
                "            <div class=\"label\">Mật khẩu tạm thời</div>\n" +
                "            <div class=\"value\">" + escapeHtml(password) + "</div>\n" +
                "        </div>\n" +
                "        <p>Vì lý do bảo mật, vui lòng đổi mật khẩu sau khi đăng nhập thành công.</p>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>Đây là email tự động từ hệ thống Vex360. Vui lòng không phản hồi email này.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        sendHtmlMail(toEmail, subject, htmlContent);
    }

    @Async
    @Override
    public void sendPartnershipApprovedEmail(
            String toEmail,
            String fullName,
            Role role,
            String organizationName) {
        String displayName = fullName == null || fullName.isBlank() ? "ban" : fullName;
        String subject = "Yeu cau hop tac da duoc duyet - Vex360";
        String content = "Xin chao " + displayName + ",\n\n"
                + "Yeu cau hop tac cho " + organizationName + " da duoc duyet. "
                + "Tai khoan cua ban hien co role " + role.name() + ".\n\n"
                + "Vui long dang nhap Vex360 de hoan thien ho so cong ty neu can.\n\n"
                + "Day la email tu dong tu he thong Vex360.";
        sendMail(toEmail, subject, content);
    }

    @Async
    @Override
    public void sendPartnershipRejectedEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String reviewNote) {
        String displayName = fullName == null || fullName.isBlank() ? "ban" : fullName;
        String reason = reviewNote == null || reviewNote.isBlank()
                ? "Admin chua cung cap ly do cu the."
                : reviewNote;
        String subject = "Yeu cau hop tac chua duoc duyet - Vex360";
        String content = "Xin chao " + displayName + ",\n\n"
                + "Yeu cau hop tac cho " + organizationName + " chua duoc duyet.\n\n"
                + "Ly do: " + reason + "\n\n"
                + "Ban co the dieu chinh thong tin va gui lai yeu cau sau.\n\n"
                + "Day la email tu dong tu he thong Vex360.";
        sendMail(toEmail, subject, content);
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

    private void sendMail(String toEmail, String subject, String content) {
        log.info("Sending email - To: {}, Subject: {}, Content: {}", toEmail, subject, content);
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email has been logged but not sent via SMTP.");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent successfully via SMTP to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", toEmail, e.getMessage());
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
