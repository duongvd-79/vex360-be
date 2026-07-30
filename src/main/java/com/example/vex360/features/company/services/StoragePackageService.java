package com.example.vex360.features.company.services;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoragePackageService {

    private static final Map<String, String> ORDER_SORT_ALIASES = Map.of(
            "companyName", "company.name");

    private final StoragePackageRepository storagePackageRepository;
    private final StoragePackageOrderRepository storagePackageOrderRepository;
    private final CompanyRepository companyRepository;
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

    @Transactional(readOnly = true)
    public PageResponse<StoragePackageResponseDTO> listAllPackages(
            String keyword, String status, Pageable pageable) {
        Page<StoragePackageResponseDTO> packages = storagePackageRepository
                .searchForAdmin(normalizeKeyword(keyword), parseActiveStatus(status), pageable)
                .map(this::toDTO);
        return PageResponse.from(packages);
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
    public StoragePackageOrder createPendingOrder(User currentUser, CreateStoragePackageOrderRequest request) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);

        StoragePackage pkg = storagePackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_NOT_FOUND));

        long orderCode = System.currentTimeMillis() / 1000 * 1000000L + random.nextLong(1000000L);

        StoragePackageOrder order = StoragePackageOrder.builder()
                .company(company)
                .storagePackage(pkg)
                .orderCode(orderCode)
                .amountVnd(pkg.getPriceVnd())
                .packageNameSnapshot(pkg.getName())
                .quotaBytesSnapshot(pkg.getQuotaBytes())
                .priceVndSnapshot(pkg.getPriceVnd())
                .status(StoragePackageOrderStatus.PENDING)
                .build();
        return storagePackageOrderRepository.save(order);
    }

    @Transactional
    public StoragePackageOrderResponseDTO updateOrderCheckoutUrl(Integer orderId, String checkoutUrl) {
        StoragePackageOrder order = storagePackageOrderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_ORDER_NOT_FOUND));

        order.setCheckoutUrl(checkoutUrl);
        StoragePackageOrder saved = storagePackageOrderRepository.save(order);

        StoragePackage pkg = saved.getStoragePackage();
        return StoragePackageOrderResponseDTO.builder()
                .orderId(saved.getId())
                .orderCode(saved.getOrderCode())
                .checkoutUrl(checkoutUrl)
                .packageName(saved.getPackageNameSnapshot() != null ? saved.getPackageNameSnapshot() : pkg.getName())
                .quotaBytes(saved.getQuotaBytesSnapshot() != null ? saved.getQuotaBytesSnapshot() : pkg.getQuotaBytes())
                .amountVnd(saved.getAmountVnd())
                .status(saved.getStatus().name())
                .build();
    }

    @Transactional
    public void markPaidAndIncrementQuota(Integer storagePackageOrderId) {
        StoragePackageOrder order = storagePackageOrderRepository.findById(storagePackageOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.STORAGE_PACKAGE_ORDER_NOT_FOUND));

        if (order.getStatus() == StoragePackageOrderStatus.PAID) {
            log.warn("StoragePackageOrder {} is already PAID. Skipping duplicate quota increment.", order.getId());
            return;
        }

        order.setStatus(StoragePackageOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        storagePackageOrderRepository.save(order);

        long quotaToAdd = order.getQuotaBytesSnapshot() != null ? order.getQuotaBytesSnapshot()
                : order.getStoragePackage().getQuotaBytes();
        Company company = order.getCompany();
        companyRepository.incrementStorageQuota(company.getId(), quotaToAdd);
        company.setStorageQuotaBytes(company.getStorageQuotaBytes() + quotaToAdd);
        log.info("Storage package PAID. Company {} quota atomically increased by {}B", company.getId(), quotaToAdd);
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
    public PageResponse<AdminStoragePackageOrderResponseDTO> listAllOrders(
            String keyword, StoragePackageOrderStatus status, Pageable pageable) {
        Pageable mappedPageable = PageableUtils.remapSort(pageable, ORDER_SORT_ALIASES);
        Page<AdminStoragePackageOrderResponseDTO> orders = storagePackageOrderRepository
                .searchForAdmin(normalizeKeyword(keyword), status, mappedPageable)
                .map(o -> AdminStoragePackageOrderResponseDTO.builder()
                        .id(o.getId())
                        .orderCode(o.getOrderCode())
                        .companyName(o.getCompany().getName())
                        .packageName(o.getPackageNameSnapshot() != null ? o.getPackageNameSnapshot()
                                : o.getStoragePackage().getName())
                        .quotaBytes(o.getQuotaBytesSnapshot() != null ? o.getQuotaBytesSnapshot()
                                : o.getStoragePackage().getQuotaBytes())
                        .amountVnd(o.getAmountVnd())
                        .status(o.getStatus().name())
                        .paidAt(o.getPaidAt())
                        .createdAt(o.getCreatedAt())
                        .build());
        return PageResponse.from(orders);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Boolean parseActiveStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }

        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active", "true" -> true;
            case "inactive", "false" -> false;
            default -> throw new AppException(ErrorCode.VALIDATION_FAILED);
        };
    }

}
