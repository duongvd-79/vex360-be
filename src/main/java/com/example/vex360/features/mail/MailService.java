package com.example.vex360.features.mail;

public interface MailService {
    void sendForgotPasswordEmail(String toEmail, String resetToken);
    void sendPasswordChangeVerificationEmail(String toEmail, String changeToken);
}
