package com.example.vex360.features.exhibition.services.impl;

import org.springframework.stereotype.Service;

import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSIntegrationServiceImpl implements PayOSIntegrationService {

    private final PayOS payOS;

    @Override
    public CreatePaymentLinkResponse createPaymentLink(Long orderCode, Long amount, String description, String returnUrl, String cancelUrl) {
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();

        log.info("Creating PayOS payment link for orderCode: {}, amount: {}", orderCode, amount);
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return payOS.paymentRequests().create(request);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/3 failed to create PayOS payment link for orderCode: {}: {}",
                        attempt, orderCode, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.error("All 3 attempts failed to create PayOS payment link for orderCode: {}", orderCode, lastException);
        throw new AppException(ErrorCode.PAYMENT_LINK_UNAVAILABLE);
    }

    @Override
    public PaymentLink getPaymentLinkInformation(Long orderCode) {
        try {
            log.info("Getting PayOS payment link info for orderCode: {}", orderCode);
            return payOS.paymentRequests().get(orderCode);
        } catch (Exception e) {
            log.error("Failed to get PayOS payment link info for orderCode: {}", orderCode, e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    @Override
    public PaymentLink cancelPaymentLink(Long orderCode, String cancellationReason) {
        try {
            log.info("Cancelling PayOS payment link for orderCode: {}, reason: {}", orderCode, cancellationReason);
            return payOS.paymentRequests().cancel(orderCode, cancellationReason);
        } catch (Exception e) {
            log.error("Failed to cancel PayOS payment link for orderCode: {}", orderCode, e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }
}
