package com.example.vex360.features.exhibition.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;

@Service
@RequiredArgsConstructor
public class StoragePaymentService {

    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;

    @Transactional
    public String createPayment(Integer storagePackageOrderId, Long orderCode, Long amount, String description,
            String returnUrl, String cancelUrl) {
        Payment payment = Payment.builder()
                .storagePackageOrderId(storagePackageOrderId)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .orderCode(orderCode)
                .amount(BigDecimal.valueOf(amount))
                .systemFee(BigDecimal.valueOf(amount))
                .organizerPayout(BigDecimal.ZERO)
                .paymentProvider("PAYOS")
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        try {
            CreatePaymentLinkResponse response = payOSIntegrationService.createPaymentLink(
                    orderCode, amount, description, returnUrl, cancelUrl);
            payment.setCheckoutUrl(response.getCheckoutUrl());
            paymentRepository.save(payment);
            return response.getCheckoutUrl();
        } catch (Exception e) {
            try {
                PaymentLink linkData = payOSIntegrationService
                        .getPaymentLinkInformation(orderCode);
                if (linkData != null) {
                    payment.setStatus(PaymentStatus.PENDING);
                    paymentRepository.save(payment);
                }
            } catch (Exception ex) {
                // Ignore secondary failure
            }
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }
}
