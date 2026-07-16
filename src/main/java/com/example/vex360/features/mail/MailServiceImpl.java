package com.example.vex360.features.mail;

import org.springframework.beans.factory.ObjectProvider;
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
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
        String subject = "Yêu cầu hợp tác đã được phê duyệt - Vex360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Yêu cầu hợp tác đã được phê duyệt</title>\n" +
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
                "        .info-box {\n" +
                "            background: rgba(15, 23, 42, 0.72);\n" +
                "            border: 1px solid rgba(148, 163, 184, 0.18);\n" +
                "            border-radius: 12px;\n" +
                "            padding: 20px;\n" +
                "            margin: 28px 0;\n" +
                "        }\n" +
                "        .label {\n" +
                "            color: #64748b;\n" +
                "            font-size: 13px;\n" +
                "            margin-bottom: 6px;\n" +
                "        }\n" +
                "        .value {\n" +
                "            color: #ffffff;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
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
                "        <h2>Yêu cầu hợp tác đã được phê duyệt!</h2>\n" +
                "        <p>Xin chào " + escapeHtml(displayName) + ",</p>\n" +
                "        <p>Chúc mừng bạn! Yêu cầu hợp tác cho tổ chức <strong>" + escapeHtml(organizationName) + "</strong> đã được phê duyệt thành công.</p>\n" +
                "        <div class=\"info-box\">\n" +
                "            <div class=\"label\">Tên tổ chức</div>\n" +
                "            <div class=\"value\">" + escapeHtml(organizationName) + "</div>\n" +
                "            <div class=\"label\">Vai trò được cấp</div>\n" +
                "            <div class=\"value\">" + role.name() + "</div>\n" +
                "        </div>\n" +
                "        <p>Vui lòng đăng nhập vào hệ thống VEX360 để hoàn thiện hồ sơ công ty và bắt đầu sử dụng dịch vụ dành riêng cho đối tác.</p>\n" +
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
    public void sendPartnershipRejectedEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String reviewNote) {
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
        String reason = reviewNote == null || reviewNote.isBlank()
                ? "Ban quản trị chưa cung cấp lý do cụ thể."
                : reviewNote;
        String subject = "Yêu cầu hợp tác chưa được phê duyệt - Vex360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Yêu cầu hợp tác chưa được phê duyệt</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
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
                "        .reason-box {\n" +
                "            background-color: #f5f0e8;\n" +
                "            border-left: 4px solid #cc785c;\n" +
                "            border-top: 1px solid #e6dfd8;\n" +
                "            border-right: 1px solid #e6dfd8;\n" +
                "            border-bottom: 1px solid #e6dfd8;\n" +
                "            border-radius: 8px;\n" +
                "            padding: 16px;\n" +
                "            margin: 24px 0;\n" +
                "        }\n" +
                "        .reason-title {\n" +
                "            font-weight: 600;\n" +
                "            font-size: 14px;\n" +
                "            color: #cc785c;\n" +
                "            margin: 0 0 8px 0;\n" +
                "        }\n" +
                "        .reason-text {\n" +
                "            font-size: 15px;\n" +
                "            margin: 0;\n" +
                "            color: #3d3d3a;\n" +
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
                "        <h2>Yêu cầu hợp tác chưa được phê duyệt</h2>\n" +
                "        <p>Xin chào " + escapeHtml(displayName) + ",</p>\n" +
                "        <p>Cảm ơn bạn đã quan tâm và gửi yêu cầu hợp tác cho tổ chức <strong>" + escapeHtml(organizationName) + "</strong> trên hệ thống VEX360.</p>\n" +
                "        <p>Rất tiếc, sau khi xem xét kỹ lưỡng, chúng tôi chưa thể phê duyệt yêu cầu hợp tác của bạn vào lúc này.</p>\n" +
                "        <div class=\"reason-box\">\n" +
                "            <p class=\"reason-title\">Lý do từ chối:</p>\n" +
                "            <p class=\"reason-text\">" + escapeHtml(reason) + "</p>\n" +
                "        </div>\n" +
                "        <p>Bạn có thể điều chỉnh thông tin cần thiết và thực hiện gửi lại yêu cầu hợp tác sau.</p>\n" +
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
    public void sendPartnershipVerificationEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String confirmUrl) {
        String displayName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
        String subject = "Xác nhận yêu cầu hợp tác - Vex360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Xác minh yêu cầu hợp tác</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
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
                "        .btn-group {\n" +
                "            text-align: center;\n" +
                "            margin: 32px 0;\n" +
                "        }\n" +
                "        .btn-confirm {\n" +
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
                "        <h2>Xác nhận yêu cầu hợp tác</h2>\n" +
                "        <p>Xin chào " + escapeHtml(displayName) + ",</p>\n" +
                "        <p>Chúng tôi nhận được yêu cầu hợp tác cho tổ chức <strong>" + escapeHtml(organizationName) + "</strong> của bạn trên hệ thống VEX360. Vui lòng xác thực yêu cầu này bằng cách lựa chọn hành động bên dưới (liên kết này có hiệu lực trong vòng 24 giờ):</p>\n" +
                "        <div class=\"btn-group\">\n" +
                "            <a href=\"" + confirmUrl + "\" class=\"btn-confirm\">Xác nhận gửi yêu cầu</a>\n" +
                "        </div>\n" +
                "        <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.</p>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>Đây là email tự động từ hệ thống VEX360. Vui lòng không phản hồi email này.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
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
