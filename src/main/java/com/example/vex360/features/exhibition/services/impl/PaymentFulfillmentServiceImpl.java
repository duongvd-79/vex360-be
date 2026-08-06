package com.example.vex360.features.exhibition.services.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentReceiptRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.repositories.PaymentRepository.PaymentRoute;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentReceiptStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.model.webhooks.WebhookData;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFulfillmentServiceImpl implements PaymentFulfillmentService {

    private final PaymentReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final ExhibitorRegistrationRepository registrationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExhibitionTimelinePolicy timelinePolicy;

    @Override
    @Transactional
    public PaymentReceipt recordReceipt(WebhookData webhookData) {
        if (webhookData == null) {
            log.error("[PB-005] Cannot record receipt: null webhookData");
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }

        Long orderCode = webhookData.getOrderCode();
        Optional<PaymentReceipt> existingOpt = receiptRepository.findByOrderCode(orderCode);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        // Create new durable receipt
        String sanitizedPayload = String.format("{\"orderCode\":%d,\"code\":\"%s\",\"currency\":\"%s\",\"amount\":%d}",
                orderCode,
                webhookData.getCode(),
                webhookData.getCurrency(),
                webhookData.getAmount());

        PaymentReceipt receipt = PaymentReceipt.builder()
                .orderCode(orderCode)
                .paymentReference(webhookData.getReference())
                .status(PaymentReceiptStatus.PENDING)
                .retryCount(0)
                .payload(sanitizedPayload)
                .build();

        log.info("[PB-005] Durable payment receipt recorded for orderCode: {}", orderCode);
        return receiptRepository.save(receipt);
    }

    @Override
    @Transactional
    public void updateReceiptSucceeded(Long orderCode, Integer registrationId, UUID boothId) {
        if (orderCode == null) {
            return;
        }

        PaymentReceipt receipt = receiptRepository.findByOrderCodeForUpdate(orderCode)
                .orElseGet(() -> PaymentReceipt.builder()
                        .orderCode(orderCode)
                        .status(PaymentReceiptStatus.PENDING)
                        .retryCount(0)
                        .build());

        receipt.setStatus(PaymentReceiptStatus.SUCCEEDED);
        receipt.setRegistrationId(registrationId);
        receipt.setBoothId(boothId);
        receipt.setLastError(null);
        receipt.setNextRetryAt(null);

        receiptRepository.save(receipt);
        log.info("[PB-005] Receipt updated to SUCCEEDED for orderCode: {}, registrationId: {}, boothId: {}",
                orderCode, registrationId, boothId);
    }

    @Override
    @Transactional
    public void updateReceiptFailed(Long orderCode, Throwable throwable) {
        if (orderCode == null) {
            return;
        }

        PaymentReceipt receipt = receiptRepository.findByOrderCodeForUpdate(orderCode)
                .orElseGet(() -> PaymentReceipt.builder()
                        .orderCode(orderCode)
                        .status(PaymentReceiptStatus.PENDING)
                        .retryCount(0)
                        .build());

        int nextRetryCount = receipt.getRetryCount() + 1;
        receipt.setRetryCount(nextRetryCount);
        receipt.setLastError(throwable != null ? throwable.getMessage() : "Fulfillment error");

        boolean nonRetryable = isNonRetryableException(throwable) || nextRetryCount >= 5;
        if (nonRetryable) {
            receipt.setStatus(PaymentReceiptStatus.MANUAL_REVIEW);
            receipt.setNextRetryAt(null);
            log.error("[PB-005/006] Receipt moved to MANUAL_REVIEW for orderCode: {}, retries: {}, error: {}",
                    orderCode, nextRetryCount, receipt.getLastError());
        } else {
            receipt.setStatus(PaymentReceiptStatus.RETRYABLE_FAILED);
            long backoffSeconds = (long) Math.pow(2, Math.min(nextRetryCount, 6)) * 60L;
            receipt.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            log.warn("[PB-005/006] Receipt set to RETRYABLE_FAILED for orderCode: {}, nextRetryAt: {}",
                    orderCode, receipt.getNextRetryAt());
        }

        receiptRepository.save(receipt);
    }

    @Override
    @Transactional
    public Optional<PaymentReceipt> processFulfillmentForOrderCode(Long orderCode) {
        if (orderCode == null) {
            return Optional.empty();
        }

        Optional<PaymentRoute> route = paymentRepository.findRouteByOrderCode(orderCode);
        if (route.isEmpty() || route.get().getPaymentType() != PaymentType.EXHIBITION_REGISTRATION
                || route.get().getRegistrationId() == null) {
            return Optional.empty();
        }

        Integer regId = route.get().getRegistrationId();
        ExhibitorRegistration registration = registrationRepository.findByIdForUpdate(regId).orElse(null);
        if (registration == null) {
            log.error("[PB-006] Registration missing for orderCode {}", orderCode);
            updateReceiptFailed(orderCode, new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
            return receiptRepository.findByOrderCode(orderCode);
        }

        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PAID) {
            return Optional.empty();
        }

        boolean registrationOpen = registration.getExhibitionPackage() != null
                && timelinePolicy.isRegistrationOpen(registration.getExhibitionPackage().getExhibition());
        if (!registrationOpen
                || registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT
                        && registration.getStatus() != ExhibitorRegistrationStatus.APPROVED) {
            updateReceiptFailed(orderCode, new AppException(ErrorCode.REGISTRATION_CLOSED));
            return receiptRepository.findByOrderCode(orderCode);
        }

        boolean newlyApproved = registration.getStatus() != ExhibitorRegistrationStatus.APPROVED;
        if (newlyApproved) {
            registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
            registrationRepository.save(registration);
        }

        try {
            updateReceiptSucceeded(orderCode, regId, null);
            if (newlyApproved) {
                eventPublisher.publishEvent(new ExhibitorRegistrationApprovedEvent(this, registration));
            }
            log.info("[PB-006] Reconciliation auto-fulfilled for orderCode {}", orderCode);
        } catch (Throwable t) {
            updateReceiptFailed(orderCode, t);
        }

        return receiptRepository.findByOrderCode(orderCode);
    }

    @Override
    @Transactional
    public Optional<PaymentReceipt> replayFulfillment(Long orderCode, String auditActor) {
        log.info("[PB-006 Audit] Admin/Reconciliation replay triggered by actor: {} for orderCode: {}",
                auditActor, orderCode);

        PaymentReceipt receipt = receiptRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (receipt != null && receipt.getStatus() == PaymentReceiptStatus.MANUAL_REVIEW) {
            receipt.setStatus(PaymentReceiptStatus.PENDING);
            receiptRepository.save(receipt);
        }

        return processFulfillmentForOrderCode(orderCode);
    }

    private boolean isNonRetryableException(Throwable t) {
        if (t instanceof AppException appEx) {
            return appEx.getErrorCode() == ErrorCode.REGISTRATION_DEPENDENCY_INVALID
                    || appEx.getErrorCode() == ErrorCode.REGISTRATION_NOT_FOUND
                    || appEx.getErrorCode() == ErrorCode.REGISTRATION_CLOSED;
        }
        return false;
    }
}
