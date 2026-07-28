package com.example.vex360.features.exhibition.services;

import java.util.Optional;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import vn.payos.model.webhooks.WebhookData;

public interface PaymentFulfillmentService {
    PaymentReceipt recordReceipt(WebhookData webhookData);
    Optional<PaymentReceipt> processFulfillmentForOrderCode(Long orderCode);
    Optional<PaymentReceipt> replayFulfillment(Long orderCode, String auditActor);
    void updateReceiptSucceeded(Long orderCode, Integer registrationId, java.util.UUID boothId);
    void updateReceiptFailed(Long orderCode, Throwable throwable);
}
