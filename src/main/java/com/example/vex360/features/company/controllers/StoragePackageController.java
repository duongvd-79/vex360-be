package com.example.vex360.features.company.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/storage-packages")
@RequiredArgsConstructor
@Tag(name = "Storage Packages", description = "Gói dịch vụ lưu trữ cho exhibitor")
public class StoragePackageController extends BaseController {

    private final StoragePackageService storagePackageService;

    @GetMapping
    @Operation(summary = "Danh sách gói lưu trữ", description = "Trả về các gói đang active, sắp xếp theo giá tăng dần.")
    public ResponseEntity<ApiResponse<List<StoragePackageResponseDTO>>> listPackages() {
        return ok(storagePackageService.listActivePackages());
    }
}
