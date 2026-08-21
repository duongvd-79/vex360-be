package com.example.vex360.features.company.controllers;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.AdminStoragePackageOrderResponseDTO;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;

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
    @Operation(summary = "Danh sách tất cả gói lưu trữ", description = "Trả về cả gói active và inactive, hỗ trợ sắp xếp theo ngày tạo; mặc định sắp xếp theo giá tăng dần.")
    public ResponseEntity<ApiResponse<PageResponse<StoragePackageResponseDTO>>> listAllPackages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "priceVnd", direction = Sort.Direction.ASC) Pageable pageable) {
        return ok(storagePackageService.listAllPackages(keyword, status, pageable));
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
    @Operation(summary = "Danh sách tất cả đơn hàng gói lưu trữ", description = "Hỗ trợ tìm kiếm theo tên doanh nghiệp hoặc mã đơn hàng, lọc trạng thái, phân trang và sắp xếp.")
    public ResponseEntity<ApiResponse<PageResponse<AdminStoragePackageOrderResponseDTO>>> listAllOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) StoragePackageOrderStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(storagePackageService.listAllOrders(keyword, status, pageable));
    }

}
