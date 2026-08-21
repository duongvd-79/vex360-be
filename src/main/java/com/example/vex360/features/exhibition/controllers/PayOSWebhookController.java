package com.example.vex360.features.exhibition.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.exhibition.services.PayOSWebhookService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.payos.model.webhooks.WebhookData;

@RestController
@RequestMapping("/api/v1/webhooks/payos")
@RequiredArgsConstructor
@Tag(name = "PayOS Webhook", description = "Các endpoint nhận phản hồi (callback) từ hệ thống PayOS")
public class PayOSWebhookController extends BaseController {

    private final PayOSWebhookService payOSWebhookService;

    @PostMapping
    @Operation(summary = "Nhận sự kiện thanh toán từ PayOS", description = "Endpoint công khai nhận webhook từ PayOS khi giao dịch thanh toán thay đổi trạng thái. Thực hiện xác thực chữ ký (signature check) và tự động cập nhật trạng thái đơn hàng.")
    public ResponseEntity<ApiResponse<WebhookData>> handlePayOSWebhook(@RequestBody Object body) {
        WebhookData data = payOSWebhookService.handleWebhook(body);
        return ok(data);
    }
}
