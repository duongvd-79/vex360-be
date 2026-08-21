package com.example.vex360.features.exhibition.services;

import vn.payos.model.webhooks.WebhookData;

public interface PayOSWebhookService {
    WebhookData handleWebhook(Object body);
}
