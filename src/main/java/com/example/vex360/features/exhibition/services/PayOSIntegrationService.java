package com.example.vex360.features.exhibition.services;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

public interface PayOSIntegrationService {
    CreatePaymentLinkResponse createPaymentLink(Long orderCode, Long amount, String description, String returnUrl, String cancelUrl);
}
