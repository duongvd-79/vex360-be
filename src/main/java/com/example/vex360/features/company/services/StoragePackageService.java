package com.example.vex360.features.company.services;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.response.AdminStoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.request.CreateStoragePackageRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoragePackageService {

    private final StoragePackageRepository storagePackageRepository;
    private final StoragePackageOrderRepository storagePackageOrderRepository;
    private final CompanyRepository companyRepository;
    private final StoragePaymentService storagePaymentService;
    private final CompanyService companyService;

    @Value("${app.payos.storage-return-url:http://localhost:5175/storage/payment/success}")
    private String returnUrl;

    @Value("${app.payos.storage-cancel-url:http://localhost:5175/storage/payment/cancel}")
    private String cancelUrl;

    private final Random random = new Random();

    public List<StoragePackageResponseDTO> listActivePackages() {
        return storagePackageRepository.findByIsActiveTrueOrderByPriceVndAsc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<StoragePackageResponseDTO> listAllPackages() {
        return storagePackageRepository.findAllByOrderByPriceVndAsc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public StoragePackageResponseDTO createPackage(CreateStoragePackageRequest request) {
        if (storagePackageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new AppException(ErrorCode.STORAGE_PACKAGE_NAME_DUPLICATED);
        }
        StoragePackage pkg = StoragePackage.builder()
                .name(request.getName())
                .description(request.getDescription())
                .quotaBytes(request.getQuotaBytes())
                .priceVnd(request.getPriceVnd())
                .isActive(true)
                .build();
        return toDTO(storagePackageRepository.save(pkg));
    }

    @Transactional
    public StoragePackageResponseDTO togglePackageStatus(Integer id) {
        StoragePackage pkg = storagePackageRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_NOT_FOUND));
        pkg.setIsActive(!pkg.getIsActive());
        return toDTO(storagePackageRepository.save(pkg));
    }

    private StoragePackageResponseDTO toDTO(StoragePackage p) {
        return StoragePackageResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .quotaBytes(p.getQuotaBytes())
                .priceVnd(p.getPriceVnd())
                .isActive(p.getIsActive())
                .build();
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

        String description = "Nang cap luu tru";
        String checkoutUrl = storagePaymentService.createPayment(
                order.getId(), orderCode, pkg.getPriceVnd(), description, returnUrl, cancelUrl);

        order.setCheckoutUrl(checkoutUrl);
        storagePackageOrderRepository.save(order);

        return StoragePackageOrderResponseDTO.builder()
                .orderId(order.getId())
                .orderCode(orderCode)
                .checkoutUrl(checkoutUrl)
                .packageName(pkg.getName())
                .quotaBytes(pkg.getQuotaBytes())
                .amountVnd(pkg.getPriceVnd())
                .status(order.getStatus().name())
                .build();
    }

    @EventListener
    @Transactional
    public void handleStoragePackagePaymentCompleted(StoragePackagePaymentCompletedEvent event) {
        StoragePackageOrder order = storagePackageOrderRepository.findById(event.getStoragePackageOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_ORDER_NOT_FOUND));
        order.setStatus(StoragePackageOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        storagePackageOrderRepository.save(order);

        Company company = order.getCompany();
        company.setStorageQuotaBytes(
                company.getStorageQuotaBytes() + order.getStoragePackage().getQuotaBytes());
        companyRepository.save(company);
        log.info("Storage package PAID. Company {} quota increased by {}B", company.getId(),
                order.getStoragePackage().getQuotaBytes());
    }

    public StorageUsageResponseDTO getUsage(User currentUser) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);
        long used = company.getStorageUsedBytes();
        long reserved = company.getStorageReservedBytes() == null ? 0 : company.getStorageReservedBytes();
        long quota = company.getStorageQuotaBytes();
        double percentage = quota > 0 ? (double) used / quota * 100 : 0;
        return StorageUsageResponseDTO.builder()
                .usedBytes(used)
                .reservedBytes(reserved)
                .quotaBytes(quota)
                .availableBytes(Math.max(0, quota - used - reserved))
                .usedPercentage(Math.round(percentage * 10.0) / 10.0)
                .build();
    }

    public StoragePackageResponseDTO updatePackage(Integer id, CreateStoragePackageRequest request) {
        StoragePackage pkg = storagePackageRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_NOT_FOUND));
        if (!pkg.getName().equalsIgnoreCase(request.getName())
                && storagePackageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new AppException(ErrorCode.STORAGE_PACKAGE_NAME_DUPLICATED);
        }
        pkg.setName(request.getName());
        pkg.setDescription(request.getDescription());
        pkg.setQuotaBytes(request.getQuotaBytes());
        pkg.setPriceVnd(request.getPriceVnd());
        return toDTO(storagePackageRepository.save(pkg));
    }

    @Transactional(readOnly = true)
    public List<AdminStoragePackageOrderResponseDTO> listAllOrders() {
        return storagePackageOrderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(o -> AdminStoragePackageOrderResponseDTO.builder()
                        .id(o.getId())
                        .orderCode(o.getOrderCode())
                        .companyName(o.getCompany().getName())
                        .packageName(o.getStoragePackage().getName())
                        .quotaBytes(o.getStoragePackage().getQuotaBytes())
                        .amountVnd(o.getAmountVnd())
                        .status(o.getStatus().name())
                        .paidAt(o.getPaidAt())
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();
    }

}
