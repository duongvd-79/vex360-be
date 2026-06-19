package com.example.vex360.features.mail;

public interface MailService {
    void sendForgotPasswordEmail(String toEmail, String resetUrl);
    void sendPasswordChangeVerificationEmail(String toEmail, String changeToken);
}
