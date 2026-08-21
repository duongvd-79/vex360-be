package com.example.vex360.features.exhibition.jobs;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegistrationReservationExpiryJob {

    ExhibitorRegistrationRepository registrationRepository;
    PaymentRepository paymentRepository;
    PayOSIntegrationService payOSIntegrationService;

    private static final int BATCH_SIZE = 100;

    @Scheduled(cron = "${app.exhibition.reservation-expiry-cron:0 */5 * * * *}", zone = "UTC")
    @Transactional
    public void cleanupExpiredReservations() {
        Instant now = Instant.now();
        List<ExhibitorRegistration> expiredList = registrationRepository.findExpiredPendingRegistrations(
                now, PageRequest.of(0, BATCH_SIZE));
        if (expiredList.isEmpty()) {
            return;
        }

        log.info("[Reservation Expiry Job] Found {} expired registration reservation(s)", expiredList.size());
        for (ExhibitorRegistration reg : expiredList) {
            reg.setStatus(ExhibitorRegistrationStatus.EXPIRED);
            registrationRepository.save(reg);

            List<Payment> payments = paymentRepository.findByExhibitorRegistrationIdForUpdate(reg.getId());
            for (Payment p : payments) {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    if (p.getOrderCode() != null) {
                        try {
                            payOSIntegrationService.cancelPaymentLink(p.getOrderCode(),
                                    "Reservation expired after 24h");
                        } catch (Exception e) {
                            log.warn("[Reservation Expiry Job] Failed to cancel PayOS link for orderCode {}: {}",
                                    p.getOrderCode(), e.getMessage());
                        }
                    }
                    p.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(p);
                }
            }
        }
        log.info("[Reservation Expiry Job] Successfully expired {} registration(s)", expiredList.size());
    }
}
