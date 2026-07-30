package com.example.vex360.features.exhibition.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/storage-packages")
@RequiredArgsConstructor
@Tag(name = "Storage Packages", description = "Gói dịch vụ lưu trữ cho exhibitor")
public class StoragePackagePaymentController extends BaseController {

    private final StoragePaymentService storagePaymentService;

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('EXHIBITOR')")
    @RequireActiveCompany(roles = Role.EXHIBITOR)
    @Operation(summary = "Tạo đơn hàng nâng cấp lưu trữ", description = "Tạo đơn hàng và trả về checkout URL PayOS.")
    public ResponseEntity<ApiResponse<StoragePackageOrderResponseDTO>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateStoragePackageOrderRequest request) {
        return ok(storagePaymentService.createStorageOrderPayment(userDetails.getUser(), request),
                "Tạo đơn hàng thành công! Vui lòng thanh toán để kích hoạt gói.");
    }
}
