package com.example.vex360.features.exhibition.listeners;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitorRegistrationApprovedMailListener {

    private final ExhibitorRegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final MailService mailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleExhibitorRegistrationApproved(ExhibitorRegistrationApprovedEvent event) {
        if (event == null || event.getRegistration() == null || event.getRegistration().getId() == null) {
            return;
        }

        Integer registrationId = event.getRegistration().getId();
        log.info("Handling ExhibitorRegistrationApprovedEvent mail listener for registration ID: {}", registrationId);

        try {
            ExhibitorRegistration registration = registrationRepository.findById(registrationId).orElse(null);
            if (registration == null) {
                log.warn("Registration not found for ID {} in mail listener", registrationId);
                return;
            }

            Company company = registration.getCompany();
            User recipient = company != null ? company.getOwnerUser() : null;
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                log.warn("Recipient owner or email missing for registration ID {}", registrationId);
                return;
            }

            Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registrationId)
                    .orElse(null);

            ExhibitionPackage pkg = registration.getExhibitionPackage();
            Exhibition exhibition = pkg != null ? pkg.getExhibition() : null;
            String exhibitionName = exhibition != null ? exhibition.getName() : "";
            String companyName = company != null ? company.getName() : "";
            String packageName = registration.getPackageNameSnapshot() != null && !registration.getPackageNameSnapshot().isBlank()
                    ? registration.getPackageNameSnapshot()
                    : ((pkg != null && pkg.getTemplate() != null) ? pkg.getTemplate().getName() : "");
            String snapshotCurrency = registration.getCurrencySnapshot() != null
                    && !registration.getCurrencySnapshot().isBlank()
                            ? registration.getCurrencySnapshot()
                            : null;
            String paymentCurrency = payment != null && payment.getCurrency() != null
                    && !payment.getCurrency().isBlank()
                            ? payment.getCurrency()
                            : null;
            String currency = snapshotCurrency != null
                    ? snapshotCurrency
                    : (paymentCurrency != null ? paymentCurrency : "VND");

            boolean isFreePayment = payment != null && ("FREE".equalsIgnoreCase(payment.getPaymentProvider())
                    || (payment.getAmount() != null && payment.getAmount().compareTo(BigDecimal.ZERO) == 0));

            if (isFreePayment) {
                mailService.sendExhibitorRegistrationReviewResultEmail(
                        recipient.getEmail(),
                        recipient.getFullName(),
                        companyName,
                        exhibitionName,
                        packageName,
                        BigDecimal.ZERO,
                        currency,
                        ExhibitorRegistrationStatus.APPROVED,
                        null);
            } else if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
                mailService.sendExhibitorRegistrationPaymentConfirmedEmail(
                        recipient.getEmail(),
                        recipient.getFullName(),
                        companyName,
                        exhibitionName,
                        packageName,
                        payment.getAmount(),
                        paymentCurrency != null ? paymentCurrency : currency,
                        payment.getOrderCode(),
                        payment.getPaidAt());
            } else if (registration.getStatus() == ExhibitorRegistrationStatus.APPROVED) {
                BigDecimal finalPrice = registration.getFinalPriceSnapshot() != null
                        ? registration.getFinalPriceSnapshot()
                        : (pkg != null ? pkg.getFinalPrice() : null);
                mailService.sendExhibitorRegistrationReviewResultEmail(
                        recipient.getEmail(),
                        recipient.getFullName(),
                        companyName,
                        exhibitionName,
                        packageName,
                        finalPrice,
                        currency,
                        ExhibitorRegistrationStatus.APPROVED,
                        null);
            }
        } catch (Exception e) {
            log.error("Failed to handle ExhibitorRegistrationApprovedEvent mail notification for ID {}: {}", registrationId, e.getMessage());
        }
    }
}
