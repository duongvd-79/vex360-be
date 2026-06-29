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

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSIntegrationServiceImpl implements PayOSIntegrationService {

    private final PayOS payOS;

    @Override
    public CreatePaymentLinkResponse createPaymentLink(Long orderCode, Long amount, String description, String returnUrl, String cancelUrl) {
        try {
            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount)
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            log.info("Creating PayOS payment link for orderCode: {}, amount: {}", orderCode, amount);
            return payOS.paymentRequests().create(request);
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for orderCode: {}", orderCode, e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }
}
