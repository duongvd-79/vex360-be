package com.example.vex360.features.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendForgotPasswordEmail(String toEmail, String resetToken) {
        String subject = "Reset Password Request";
        String content = "To reset your password, please use the following token:\n" + resetToken;
        sendMail(toEmail, subject, content);
    }

    @Override
    public void sendPasswordChangeVerificationEmail(String toEmail, String changeToken) {
        String subject = "Change Password Verification";
        String content = "To change your password, please use the following verification token:\n" + changeToken;
        sendMail(toEmail, subject, content);
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
