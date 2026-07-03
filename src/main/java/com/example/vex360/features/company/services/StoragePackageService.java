package com.example.vex360.features.company.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoragePackageService {

    private final StoragePackageRepository storagePackageRepository;
    private final StoragePackageOrderRepository storagePackageOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;
    private final CompanyService companyService;

    @Value("${app.payos.storage-return-url:http://localhost:5175/storage/payment/success}")
    private String returnUrl;

    @Value("${app.payos.storage-cancel-url:http://localhost:5175/storage/payment/cancel}")
    private String cancelUrl;

    private final Random random = new Random();

    public List<StoragePackageResponseDTO> listActivePackages() {
        return storagePackageRepository.findByIsActiveTrueOrderByPriceVndAsc()
                .stream()
                .map(p -> StoragePackageResponseDTO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .quotaBytes(p.getQuotaBytes())
                        .priceVnd(p.getPriceVnd())
                        .build())
                .toList();
    }

    @Transactional
    public StoragePackageOrderResponseDTO createOrder(User currentUser, CreateStoragePackageOrderRequest request) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);

        StoragePackage pkg = storagePackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_NOT_FOUND));

        long orderCode = System.currentTimeMillis() / 1000 * 1000000L + random.nextLong(1000000L);

        StoragePackageOrder order = StoragePackageOrder.builder()
                .company(company)
                .storagePackage(pkg)
                .orderCode(orderCode)
                .amountVnd(pkg.getPriceVnd())
                .status(StoragePackageOrderStatus.PENDING)
                .build();
        order = storagePackageOrderRepository.save(order);

        Payment payment = Payment.builder()
                .storagePackageOrder(order)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .orderCode(orderCode)
                .amount(BigDecimal.valueOf(pkg.getPriceVnd()))
                .systemFee(BigDecimal.valueOf(pkg.getPriceVnd()))
                .organizerPayout(BigDecimal.ZERO)
                .paymentProvider("PAYOS")
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        String description = "Nang cap luu tru";
        CreatePaymentLinkResponse response = payOSIntegrationService.createPaymentLink(
                orderCode, pkg.getPriceVnd(), description, returnUrl, cancelUrl);

        order.setCheckoutUrl(response.getCheckoutUrl());
        payment.setCheckoutUrl(response.getCheckoutUrl());
        storagePackageOrderRepository.save(order);
        paymentRepository.save(payment);

        log.info("Created storage package order {} for company {}", orderCode, company.getId());

        return StoragePackageOrderResponseDTO.builder()
                .orderId(order.getId())
                .orderCode(orderCode)
                .checkoutUrl(response.getCheckoutUrl())
                .packageName(pkg.getName())
                .quotaBytes(pkg.getQuotaBytes())
                .amountVnd(pkg.getPriceVnd())
                .status(order.getStatus().name())
                .build();
    }

    public StorageUsageResponseDTO getUsage(User currentUser) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);
        long used = company.getStorageUsedBytes();
        long quota = company.getStorageQuotaBytes();
        double percentage = quota > 0 ? (double) used / quota * 100 : 0;
        return StorageUsageResponseDTO.builder()
                .usedBytes(used)
                .quotaBytes(quota)
                .usedPercentage(Math.round(percentage * 10.0) / 10.0)
                .build();
    }
}
