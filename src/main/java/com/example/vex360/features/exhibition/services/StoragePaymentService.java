package com.example.vex360.features.exhibition.services;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@Service
@RequiredArgsConstructor
public class StoragePaymentService {

    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;
    private final StoragePackageService storagePackageService;

    @Value("${app.payos.storage-return-url:http://localhost:5175/storage/payment/success}")
    private String returnUrl;

    @Value("${app.payos.storage-cancel-url:http://localhost:5175/storage/payment/cancel}")
    private String cancelUrl;

    @Transactional
    public StoragePackageOrderResponseDTO createStorageOrderPayment(User currentUser,
            CreateStoragePackageOrderRequest request) {
        StoragePackageOrder order = storagePackageService.createPendingOrder(currentUser, request);
        String description = "Nang cap luu tru";
        String checkoutUrl = createPayment(
                order.getId(), order.getOrderCode(), order.getAmountVnd(), description, returnUrl, cancelUrl);
        return storagePackageService.updateOrderCheckoutUrl(order.getId(), checkoutUrl);
    }

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
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new AppException(ErrorCode.PAYMENT_LINK_UNAVAILABLE);
        }
    }
}
