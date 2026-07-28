package com.example.vex360.features.exhibition.jobs;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
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

    @Scheduled(fixedDelayString = "${app.payment.reconciliation-delay-ms:60000}")
    public void runReconciliation() {
        log.info("[PB-006 Reconciliation Job] Starting auto-repair reconciliation run...");

        // 1. Process claimable receipts (PENDING or RETRYABLE_FAILED)
        List<PaymentReceipt> claimableReceipts = receiptRepository.findClaimableReceipts(
                List.of(PaymentReceiptStatus.PENDING, PaymentReceiptStatus.RETRYABLE_FAILED),
                Instant.now(),
                PageRequest.of(0, 50));

        for (PaymentReceipt receipt : claimableReceipts) {
            try {
                log.info("[PB-006] Processing claimable receipt for orderCode {}", receipt.getOrderCode());
                fulfillmentService.processFulfillmentForOrderCode(receipt.getOrderCode());
            } catch (Exception e) {
                log.error("[PB-006] Exception processing receipt for orderCode {}", receipt.getOrderCode(), e);
            }
        }

        // 2. Process orphan PAID payments missing a booth
        List<Payment> unfulfilledPaid = paymentRepository.findUnfulfilledPaidPayments(PageRequest.of(0, 50));
        for (Payment payment : unfulfilledPaid) {
            try {
                log.info("[PB-006] Processing unfulfilled PAID payment orderCode {}", payment.getOrderCode());
                fulfillmentService.processFulfillmentForOrderCode(payment.getOrderCode());
            } catch (Exception e) {
                log.error("[PB-006] Exception processing unfulfilled PAID payment orderCode {}", payment.getOrderCode(), e);
            }
        }

        // 3. Query PayOS for PENDING local payments that may have paid on provider side
        List<Payment> pendingPayments = paymentRepository.findPendingExhibitionPayments(PageRequest.of(0, 50));
        for (Payment payment : pendingPayments) {
            if (payment.getOrderCode() == null) {
                continue;
            }
            try {
                PaymentLink linkInfo = payOSIntegrationService.getPaymentLinkInformation(payment.getOrderCode());
                if (linkInfo != null && linkInfo.getStatus() == PaymentLinkStatus.PAID) {
                    log.info("[PB-006] Provider confirmed PAID for local PENDING orderCode {}. Fulfilling...", payment.getOrderCode());
                    payment.setStatus(PaymentStatus.PAID);
                    payment.setPaidAt(Instant.now());
                    paymentRepository.save(payment);
                    fulfillmentService.processFulfillmentForOrderCode(payment.getOrderCode());
                }
            } catch (Exception e) {
                log.debug("[PB-006] PayOS status check exception for orderCode {}: {}", payment.getOrderCode(), e.getMessage());
            }
        }

        log.info("[PB-006 Reconciliation Job] Reconciliation run finished.");
    }
}
