package com.example.vex360.features.exhibition.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.features.exhibition.events.ExhibitionPaymentCompletedEvent;
import com.example.vex360.features.exhibition.repositories.PaymentReceiptRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.shared.enums.PaymentReceiptStatus;
import com.example.vex360.shared.enums.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    private final PaymentReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService fulfillmentService;
    private final PayOSIntegrationService payOSIntegrationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Duration PAYOS_LINK_EXPIRY = Duration.ofHours(24);

    @Scheduled(fixedDelayString = "${app.payment.reconciliation-delay-ms:60000}")
    public void runReconciliation() {
        // 1. Process claimable receipts (PENDING or RETRYABLE_FAILED)
        List<PaymentReceipt> claimableReceipts = receiptRepository.findClaimableReceipts(
                List.of(PaymentReceiptStatus.PENDING, PaymentReceiptStatus.RETRYABLE_FAILED),
                Instant.now(),
                PageRequest.of(0, 50));

        // 2. Process orphan PAID payments missing a booth
        List<Payment> unfulfilledPaid = paymentRepository.findUnfulfilledPaidPayments(PageRequest.of(0, 50));

        // 3. Query PayOS for PENDING local payments that may have paid on provider side
        List<Payment> pendingPayments = paymentRepository.findPendingExhibitionPayments(PageRequest.of(0, 50));

        // 4. Mark PENDING payments past the PayOS link lifetime as EXPIRED
        Instant expirationCutoff = Instant.now().minus(PAYOS_LINK_EXPIRY);
        List<Payment> expiredPendingPayments = paymentRepository.findExpiredPendingPayments(
                expirationCutoff, PageRequest.of(0, 50));

        if (claimableReceipts.isEmpty() && unfulfilledPaid.isEmpty() && pendingPayments.isEmpty()
                && expiredPendingPayments.isEmpty()) {
            return;
        }

        log.info("[PB-006 Reconciliation Job] Starting auto-repair reconciliation run for {} receipts, {} unfulfilled, {} pending payments, {} expired payments...",
                claimableReceipts.size(), unfulfilledPaid.size(), pendingPayments.size(),
                expiredPendingPayments.size());

        for (PaymentReceipt receipt : claimableReceipts) {
            try {
                log.info("[PB-006] Processing claimable receipt for orderCode {}", receipt.getOrderCode());
                fulfillmentService.processFulfillmentForOrderCode(receipt.getOrderCode());
            } catch (Exception e) {
                log.error("[PB-006] Exception processing receipt for orderCode {}", receipt.getOrderCode(), e);
            }
        }

        for (Payment payment : unfulfilledPaid) {
            try {
                log.info("[PB-006] Processing unfulfilled PAID payment orderCode {}", payment.getOrderCode());
                fulfillmentService.processFulfillmentForOrderCode(payment.getOrderCode());
                eventPublisher.publishEvent(new ExhibitionPaymentCompletedEvent(this, payment));
            } catch (Exception e) {
                log.error("[PB-006] Exception processing unfulfilled PAID payment orderCode {}", payment.getOrderCode(),
                        e);
            }
        }

        for (Payment payment : pendingPayments) {
            if (payment.getOrderCode() == null) {
                continue;
            }
            try {
                PaymentLink linkInfo = payOSIntegrationService.getPaymentLinkInformation(payment.getOrderCode());
                if (linkInfo != null) {
                    if (linkInfo.getStatus() == PaymentLinkStatus.PAID) {
                        log.info("[PB-006] Provider confirmed PAID for local PENDING orderCode {}. Fulfilling...",
                                payment.getOrderCode());
                        payment.setStatus(PaymentStatus.PAID);
                        payment.setPaidAt(Instant.now());
                        paymentRepository.save(payment);
                        fulfillmentService.processFulfillmentForOrderCode(payment.getOrderCode());
                        eventPublisher.publishEvent(new ExhibitionPaymentCompletedEvent(this, payment));
                    } else if (linkInfo.getStatus() == PaymentLinkStatus.CANCELLED) {
                        log.info("[PB-006] Provider confirmed CANCELLED for local PENDING orderCode {}. Marking FAILED...",
                                payment.getOrderCode());
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);
                    } else if (linkInfo.getStatus() == PaymentLinkStatus.EXPIRED) {
                        log.info("[PB-006] Provider confirmed EXPIRED for local PENDING orderCode {}. Marking EXPIRED...",
                                payment.getOrderCode());
                        payment.setStatus(PaymentStatus.EXPIRED);
                        paymentRepository.save(payment);
                    }
                }
            } catch (Exception e) {
                log.debug("[PB-006] PayOS status check exception for orderCode {}: {}", payment.getOrderCode(),
                        e.getMessage());
            }
        }

        for (Payment payment : expiredPendingPayments) {
            if (payment.getOrderCode() == null || payment.getStatus() != PaymentStatus.PENDING) {
                continue;
            }
            log.info("[PB-006] Payment orderCode {} pending since {} (past {}); marking EXPIRED...",
                    payment.getOrderCode(), payment.getCreatedAt(), PAYOS_LINK_EXPIRY);
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
        }

        log.info("[PB-006 Reconciliation Job] Reconciliation run finished.");
    }
}
