package com.example.vex360.features.mail;

public interface MailService {
    void sendForgotPasswordEmail(String toEmail, String resetUrl);
    void sendRegistrationVerificationEmail(String toEmail, String verifyUrl);
    void sendPasswordChangeNotificationEmail(String toEmail);
}
