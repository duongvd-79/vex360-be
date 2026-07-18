package com.example.vex360.features.company.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.AdminStoragePackageOrderResponseDTO;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/storage-packages")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Storage Packages", description = "Quản lý gói lưu trữ (Admin only)")
public class AdminStoragePackageController extends BaseController {

    private final StoragePackageService storagePackageService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả gói lưu trữ", description = "Trả về cả gói active và inactive, sắp xếp theo giá tăng dần.")
    public ResponseEntity<ApiResponse<List<StoragePackageResponseDTO>>> listAllPackages() {
        return ok(storagePackageService.listAllPackages());
    }

    @PostMapping
    @Operation(summary = "Tạo gói lưu trữ mới")
    public ResponseEntity<ApiResponse<StoragePackageResponseDTO>> createPackage(
            @Valid @RequestBody CreateStoragePackageRequest request) {
        return created(storagePackageService.createPackage(request));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt trạng thái gói lưu trữ")
    public ResponseEntity<ApiResponse<StoragePackageResponseDTO>> toggleStatus(
            @PathVariable Integer id) {
        return ok(storagePackageService.togglePackageStatus(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin gói lưu trữ")
    public ResponseEntity<ApiResponse<StoragePackageResponseDTO>> updatePackage(
            @PathVariable Integer id,
            @Valid @RequestBody CreateStoragePackageRequest request) {
        return ok(storagePackageService.updatePackage(id, request));
    }

    @GetMapping("/orders")
    @Operation(summary = "Danh sách tất cả đơn hàng gói lưu trữ")
    public ResponseEntity<ApiResponse<List<AdminStoragePackageOrderResponseDTO>>> listAllOrders() {
        return ok(storagePackageService.listAllOrders());
    }

}
