package com.example.vex360.features.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

@Component
@Profile("local")
class SmtpEmailTransport implements EmailTransport {

    private final JavaMailSender mailSender;

    SmtpEmailTransport(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("SMTP email delivery failed", exception);
        }
    }
}
