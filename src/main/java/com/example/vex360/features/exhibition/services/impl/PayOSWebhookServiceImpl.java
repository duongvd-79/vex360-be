package com.example.vex360.features.exhibition.services.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSWebhookService;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;

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
    private final StoragePackageOrderRepository storagePackageOrderRepository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public WebhookData handleWebhook(Object body) {
        try {
            // Verify webhook payload signature using CHECKSUM_KEY via PayOS SDK
            WebhookData data = payOS.webhooks().verify(body);
            log.info("Successfully verified PayOS Webhook for orderCode: {}, code: {}", data.getOrderCode(),
                    data.getCode());

            Long orderCode = data.getOrderCode();
            Payment payment = paymentRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new AppException(ErrorCode.UNCATCHED_EXCEPTION));

            if ("00".equals(data.getCode())) {
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(LocalDateTime.now());
                if (data.getReference() != null) {
                    payment.setPaymentReference(data.getReference());
                }
                paymentRepository.save(payment);

                if (payment.getPaymentType() == PaymentType.STORAGE_PACKAGE) {
                    StoragePackageOrder order = payment.getStoragePackageOrder();
                    order.setStatus(StoragePackageOrderStatus.PAID);
                    order.setPaidAt(LocalDateTime.now());
                    storagePackageOrderRepository.save(order);

                    Company company = order.getCompany();
                    company.setStorageQuotaBytes(
                            company.getStorageQuotaBytes() + order.getStoragePackage().getQuotaBytes());
                    companyRepository.save(company);

                    log.info("Storage package PAID. Company {} quota increased by {}B", company.getId(),
                            order.getStoragePackage().getQuotaBytes());
                } else {
                    ExhibitorRegistration registration = payment.getExhibitorRegistration();
                    registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                    registrationRepository.save(registration);
                    eventPublisher.publishEvent(new ExhibitorRegistrationApprovedEvent(this, registration));

                    log.info("Payment PAID. Registration ID: {} approved successfully.", registration.getId());
                }
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.warn("Payment FAILED/EXPIRED for orderCode: {}. Code: {}", orderCode, data.getCode());
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
