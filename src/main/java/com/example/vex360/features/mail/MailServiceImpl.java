package com.example.vex360.features.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        String subject = "Yêu cầu khôi phục mật khẩu - Vex360";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Khôi phục mật khẩu</title>\n" +
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
                "        .btn-container {\n" +
                "            text-align: center;\n" +
                "            margin: 32px 0;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            background: linear-gradient(135deg, #6366f1, #3b82f6);\n" +
                "            color: #ffffff !important;\n" +
                "            text-decoration: none;\n" +
                "            padding: 14px 28px;\n" +
                "            font-weight: 600;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 15px;\n" +
                "            box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);\n" +
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
                "        <h2>Yêu cầu khôi phục mật khẩu</h2>\n" +
                "        <p>Xin chào,</p>\n" +
                "        <p>Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản Vex360 của bạn. Vui lòng bấm vào nút bên dưới để tiến hành đổi mật khẩu mới (liên kết này có hiệu lực trong vòng 1 giờ):</p>\n"
                +
                "        <div class=\"btn-container\">\n" +
                "            <a href=\"" + resetUrl + "\" class=\"btn\">Khôi phục mật khẩu</a>\n" +
                "        </div>\n" +
                "        <p>Nếu nút trên không hoạt động, bạn có thể sao chép liên kết dưới đây và dán vào trình duyệt:</p>\n"
                +
                "        <p style=\"word-break: break-all;\"><a href=\"" + resetUrl + "\" style=\"color: #60a5fa;\">"
                + resetUrl + "</a></p>\n" +
                "        <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email này. Tài khoản của bạn vẫn được bảo mật.</p>\n"
                +
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
    public void sendPasswordChangeVerificationEmail(String toEmail, String changeToken) {
        String subject = "Change Password Verification";
        String content = "To change your password, please use the following verification token:\n" + changeToken;
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
}
