package com.example.vex360.features.exhibition.services.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSWebhookServiceImpl implements PayOSWebhookService {

    private final PaymentRepository paymentRepository;
    private final ExhibitorRegistrationRepository registrationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PayOS payOS;

    @Override
    @Transactional
    public WebhookData handleWebhook(Object body) {
        try {
            // Verify webhook payload signature using CHECKSUM_KEY via PayOS SDK
            WebhookData data = payOS.webhooks().verify(body);
            log.info("Successfully verified PayOS Webhook for orderCode: {}, code: {}", data.getOrderCode(),
                    data.getCode());

            Long orderCode = data.getOrderCode();
            PaymentRoute route = paymentRepository.findRouteByOrderCode(orderCode)
                    .orElseThrow(() -> {
                        log.error("Payment route not found for orderCode: {}", orderCode);
                        return new AppException(ErrorCode.UNCATCHED_EXCEPTION);
                    });

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
                log.info("Payment with orderCode {} has already been processed. Skipping.", orderCode);
                return data;
            }

            if ("00".equals(data.getCode())) {
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(Instant.now());
                if (data.getReference() != null) {
                    payment.setPaymentReference(data.getReference());
                }
                paymentRepository.save(payment);

                if (payment.getPaymentType() == PaymentType.STORAGE_PACKAGE) {
                    eventPublisher.publishEvent(new StoragePackagePaymentCompletedEvent(
                            this, payment.getStoragePackageOrderId()));
                } else {
                    if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT) {
                        log.warn("Payment PAID for registration ID {} in status {}. Refusing to approve.",
                                registration.getId(), registration.getStatus());
                    } else {
                        registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                        registrationRepository.save(registration);
                        eventPublisher.publishEvent(new ExhibitorRegistrationApprovedEvent(this, registration));
                        log.info("Payment PAID. Registration ID: {} approved successfully.", registration.getId());
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
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify PayOS Webhook payload", e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
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
