package com.example.vex360.features.exhibition.services.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository.PaymentRoute;
import com.example.vex360.features.exhibition.services.PayOSWebhookService;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.PaymentType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

import com.example.vex360.features.exhibition.events.ExhibitionPaymentCompletedEvent;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSWebhookServiceImpl implements PayOSWebhookService {

    private final PaymentRepository paymentRepository;
    private final ExhibitorRegistrationRepository registrationRepository;
    private final PaymentFulfillmentService fulfillmentService;
    private final StoragePackageService storagePackageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final PayOS payOS;

    @Override
    @Transactional
    public WebhookData handleWebhook(Object body) {
        Long currentOrderCode = null;
        try {
            // Verify webhook payload signature using CHECKSUM_KEY via PayOS SDK
            WebhookData data = payOS.webhooks().verify(body);
            log.info("Successfully verified PayOS Webhook for orderCode: {}, code: {}", data.getOrderCode(),
                    data.getCode());

            Long orderCode = data.getOrderCode();
            currentOrderCode = orderCode;
            fulfillmentService.recordReceipt(data);

            Optional<PaymentRoute> routeOpt = paymentRepository.findRouteByOrderCode(orderCode);
            if (routeOpt.isEmpty()) {
                log.warn(
                        "Received valid signed PayOS webhook for unmapped orderCode: {}. Auditing webhook without fulfillment.",
                        orderCode);
                return data;
            }
            PaymentRoute route = routeOpt.get();

            ExhibitorRegistration registration = null;
            if (route.getPaymentType() == PaymentType.EXHIBITION_REGISTRATION) {
                if (route.getRegistrationId() == null) {
                    log.error("Registration ID is null in payment route for orderCode: {}", orderCode);
                    throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
                }
                registration = registrationRepository.findByIdForUpdate(route.getRegistrationId())
                        .orElseThrow(() -> {
                            log.error("Exhibitor registration not found for ID: {}", route.getRegistrationId());
                            return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
                        });
            }

            Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                    .orElseThrow(() -> {
                        log.error("Payment not found for orderCode: {}", orderCode);
                        return new AppException(ErrorCode.UNCATCHED_EXCEPTION);
                    });

            validateLockedRoute(route, payment);

            if (payment.getStatus() == PaymentStatus.PAID) {
                if ("00".equals(data.getCode())) {
                    reconcileWebhookInvariants(data, payment);
                } else {
                    log.info("Payment with orderCode {} has already been PAID. Ignoring late failure webhook.",
                            orderCode);
                }
                return data;
            }

            if ("00".equals(data.getCode())) {
                reconcileWebhookInvariants(data, payment);

                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(Instant.now());
                if (data.getReference() != null) {
                    payment.setPaymentReference(data.getReference());
                }
                paymentRepository.save(payment);
                eventPublisher.publishEvent(new ExhibitionPaymentCompletedEvent(this, payment));

                if (payment.getPaymentType() == PaymentType.STORAGE_PACKAGE) {
                    storagePackageService.markPaidAndIncrementQuota(payment.getStoragePackageOrderId());
                    fulfillmentService.updateReceiptSucceeded(orderCode, null, null);
                } else if (registration != null) {
                    boolean registrationOpen = registration.getExhibitionPackage() != null
                            && timelinePolicy.isRegistrationOpen(
                                    registration.getExhibitionPackage().getExhibition());
                    if (!registrationOpen
                            || registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT
                                    && registration.getStatus() != ExhibitorRegistrationStatus.APPROVED) {
                        log.warn("Payment PAID but registration {} can no longer be fulfilled (status: {}).",
                                registration.getId(), registration.getStatus());
                        fulfillmentService.updateReceiptFailed(
                                orderCode, new AppException(ErrorCode.REGISTRATION_CLOSED));
                    } else {
                        ExhibitorRegistrationStatus oldStatus = registration.getStatus();
                        registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                        registrationRepository.save(registration);

                        fulfillmentService.updateReceiptSucceeded(orderCode, registration.getId(), null);
                        if (oldStatus != ExhibitorRegistrationStatus.APPROVED) {
                            eventPublisher.publishEvent(new ExhibitorRegistrationApprovedEvent(this, registration));
                            log.info("Payment PAID and approval event published for registration ID: {}",
                                    registration.getId());
                        }
                    }
                }
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.warn("Payment FAILED/EXPIRED for orderCode: {}. Code: {}", orderCode, data.getCode());
            }

            return data;
        } catch (AppException e) {
            log.error("AppException encountered in PayOS Webhook handling: {}", e.getErrorCode());
            if (currentOrderCode != null) {
                fulfillmentService.updateReceiptFailed(currentOrderCode, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify PayOS Webhook payload", e);
            if (currentOrderCode != null) {
                fulfillmentService.updateReceiptFailed(currentOrderCode, e);
            }
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private void reconcileWebhookInvariants(WebhookData data, Payment payment) {
        if (data.getCurrency() == null || !data.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
            log.error("Currency mismatch for orderCode {}: expected {}, got {}", data.getOrderCode(),
                    payment.getCurrency(), data.getCurrency());
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }

        if (data.getAmount() == null || BigDecimal.valueOf(data.getAmount()).compareTo(payment.getAmount()) != 0) {
            log.error("Amount mismatch for orderCode {}: expected {}, got {}", data.getOrderCode(), payment.getAmount(),
                    data.getAmount());
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }

        if (payment.getCheckoutUrl() != null && data.getPaymentLinkId() != null
                && !payment.getCheckoutUrl().contains(data.getPaymentLinkId())) {
            log.error("Payment link mismatch for orderCode {}: checkoutUrl={}, linkId={}", data.getOrderCode(),
                    payment.getCheckoutUrl(), data.getPaymentLinkId());
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }

        if (data.getReference() != null) {
            if (payment.getPaymentReference() != null && !payment.getPaymentReference().equals(data.getReference())) {
                log.error("Payment reference mismatch for orderCode {}: expected {}, got {}", data.getOrderCode(),
                        payment.getPaymentReference(), data.getReference());
                throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
            }

            if (paymentRepository.existsByPaymentReferenceAndIdNot(data.getReference(), payment.getId())) {
                log.error("Duplicate payment reference {} used for another payment. Rejecting orderCode {}",
                        data.getReference(), data.getOrderCode());
                throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
            }
        }
    }

    private void validateLockedRoute(PaymentRoute route, Payment payment) {
        if (payment.getPaymentType() != route.getPaymentType()) {
            log.error("Mismatch between payment type {} and route type {}", payment.getPaymentType(),
                    route.getPaymentType());
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }

        Integer lockedRegistrationId = payment.getExhibitorRegistration() == null
                ? null
                : payment.getExhibitorRegistration().getId();
        if (!java.util.Objects.equals(lockedRegistrationId, route.getRegistrationId())) {
            log.error("Mismatch between locked registration ID {} and route registration ID {}", lockedRegistrationId,
                    route.getRegistrationId());
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }
}
