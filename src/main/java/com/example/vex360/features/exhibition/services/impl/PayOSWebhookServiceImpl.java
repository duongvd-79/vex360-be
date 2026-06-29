package com.example.vex360.features.exhibition.services.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSWebhookService;
import com.example.vex360.shared.entities.ExhibitorRegistration;
import com.example.vex360.shared.entities.Payment;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

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
    private final PayOS payOS;

    @Override
    @Transactional
    public WebhookData handleWebhook(Object body) {
        try {
            // Verify webhook payload signature using CHECKSUM_KEY via PayOS SDK
            WebhookData data = payOS.webhooks().verify(body);
            log.info("Successfully verified PayOS Webhook for orderCode: {}, code: {}", data.getOrderCode(), data.getCode());

            Long orderCode = data.getOrderCode();
            Payment payment = paymentRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new AppException(ErrorCode.UNCATCHED_EXCEPTION));

            if ("00".equals(data.getCode())) {
                // Payment was successful
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(LocalDateTime.now());
                if (data.getReference() != null) {
                    payment.setPaymentReference(data.getReference());
                }
                paymentRepository.save(payment);

                // Update associated ExhibitorRegistration to APPROVED
                ExhibitorRegistration registration = payment.getExhibitorRegistration();
                registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                registrationRepository.save(registration);

                log.info("Payment PAID. Registration ID: {} approved successfully.", registration.getId());
            } else {
                // Payment failed or expired
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.warn("Payment FAILED/EXPIRED for orderCode: {}. Registration remains PENDING.", orderCode);
            }

            return data;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify PayOS Webhook payload", e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }
}
