package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentRevenueRecognitionService {

    CompanyService companyService;
    OrganizerWalletDomainService organizerWalletDomainService;

    @Transactional
    public OrganizerWalletTransaction recognizeRevenueForPayment(Payment payment) {
        if (payment == null) {
            return null;
        }

        if (payment.getPaymentType() != PaymentType.EXHIBITION_REGISTRATION) {
            log.info("[Revenue Recognition] Payment ID {} type is {}, skipping organizer revenue credit.",
                    payment.getId(), payment.getPaymentType());
            return null;
        }

        if (payment.getStatus() != PaymentStatus.PAID) {
            log.warn("[Revenue Recognition] Payment ID {} status is {}, not PAID. Skipping credit.",
                    payment.getId(), payment.getStatus());
            return null;
        }

        ExhibitorRegistration registration = payment.getExhibitorRegistration();
        if (registration == null || registration.getExhibitionPackage() == null
                || registration.getExhibitionPackage().getExhibition() == null) {
            log.error("[Revenue Recognition] Payment ID {} missing valid registration/exhibition route",
                    payment.getId());
            return null;
        }

        Exhibition exhibition = registration.getExhibitionPackage().getExhibition();
        User organizerUser = exhibition.getOrganizer();
        if (organizerUser == null) {
            log.error("[Revenue Recognition] Exhibition ID {} missing organizer", exhibition.getId());
            return null;
        }

        Company company;
        try {
            company = companyService.getCompanyEntityForCurrentUser(organizerUser);
        } catch (AppException exception) {
            log.error("[Revenue Recognition] Company not found for organizer User ID {}", organizerUser.getId());
            return null;
        }

        BigDecimal payoutAmount = payment.getOrganizerPayout();
        if (payoutAmount == null || payoutAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[Revenue Recognition] Payment ID {} organizer payout is zero or negative ({}), skipping credit.",
                    payment.getId(), payoutAmount);
            return null;
        }

        OrganizerWalletTransaction tx = organizerWalletDomainService.creditPendingPayment(
                company,
                exhibition,
                payment,
                payoutAmount,
                "Revenue credit from exhibition registration payment #" + payment.getOrderCode(),
                null);

        if (exhibition.getStatus() == ExhibitionStatus.COMPLETED) {
            log.info(
                    "[Revenue Recognition] Exhibition ID {} is COMPLETED. Releasing revenue immediately for payment ID {}.",
                    exhibition.getId(), payment.getId());
            organizerWalletDomainService.releasePendingRevenue(
                    company,
                    exhibition,
                    payoutAmount,
                    "Immediate release for payment #" + payment.getOrderCode() + " confirmed post-completion",
                    null);
        }

        return tx;
    }
}
