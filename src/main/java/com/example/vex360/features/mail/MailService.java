package com.example.vex360.features.mail;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.Role;

public interface MailService {
    void sendForgotPasswordEmail(String toEmail, String resetUrl);

    void sendRegistrationVerificationEmail(String toEmail, String verifyUrl);

    void sendPasswordChangeNotificationEmail(String toEmail);

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

    void sendPartnershipVerificationEmail(
            String toEmail,
            String fullName,
            String organizationName,
            String confirmUrl);

    void sendPartnershipGuestApprovedEmail(
            String toEmail,
            String fullName,
            String temporaryPassword,
            Role role,
            String organizationName);

    void sendExhibitionReviewResultEmail(
            String toEmail,
            String fullName,
            String exhibitionName,
            LocalDate startDate,
            LocalDate endDate,
            String resultStatus,
            String rejectedReason,
            int rejectionCount,
            Instant reviewedAt);

    void sendExhibitorRegistrationReviewResultEmail(
            String toEmail,
            String fullName,
            String companyName,
            String exhibitionName,
            String packageName,
            BigDecimal finalPrice,
            String currency,
            ExhibitorRegistrationStatus result,
            String rejectedReason);

    void sendExhibitorRegistrationPaymentConfirmedEmail(
            String toEmail,
            String fullName,
            String companyName,
            String exhibitionName,
            String packageName,
            BigDecimal amount,
            String currency,
            Long orderCode,
            Instant paidAt);

    void sendBoothReviewResultEmail(
            String toEmail,
            String fullName,
            String boothName,
            String exhibitionName,
            Integer versionNumber,
            String resultStatus,
            String rejectedReason,
            Instant reviewedAt);

    void sendDesignDraftReviewResultEmail(
            String toEmail,
            String fullName,
            String companyName,
            String boothName,
            Integer versionNumber,
            DesignRequestStatus result,
            String reviewNote);

    void sendDesignCancellationDecisionEmail(
            String toEmail,
            String fullName,
            boolean designerRecipient,
            String companyName,
            String boothName,
            String resultStatus,
            String cancellationReason,
            String resolutionNote,
            Instant resolvedAt);

    void sendBoothWarningEmail(
            String toEmail,
            String fullName,
            String boothName,
            String exhibitionName,
            String warningReason,
            Instant warnedAt);

    void sendBoothBanEmail(
            String toEmail,
            String fullName,
            String boothName,
            String exhibitionName,
            String banReason,
            Instant bannedAt);
}
