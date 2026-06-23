package com.example.vex360.features.mail;

import com.example.vex360.shared.enums.Role;

public interface MailService {
    void sendForgotPasswordEmail(String toEmail, String resetUrl);
    void sendPasswordChangeVerificationEmail(String toEmail, String changeToken);
    void sendNewUserCredentialsEmail(String toEmail, String fullName, String password);
    void sendPartnershipApprovedEmail(
            String toEmail,
            String fullName,
            Role role,
            String organizationName);
    void sendPartnershipRejectedEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String reviewNote);
}
