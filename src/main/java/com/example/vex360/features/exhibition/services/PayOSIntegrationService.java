package com.example.vex360.features.exhibition.services;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;

public interface PayOSIntegrationService {
    CreatePaymentLinkResponse createPaymentLink(Long orderCode, Long amount, String description, String returnUrl, String cancelUrl);

    PaymentLink getPaymentLinkInformation(Long orderCode);

    PaymentLink cancelPaymentLink(Long orderCode, String cancellationReason);
}
