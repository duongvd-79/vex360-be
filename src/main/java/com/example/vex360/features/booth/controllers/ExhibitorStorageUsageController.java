package com.example.vex360.features.booth.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/storage-packages")
@RequiredArgsConstructor
@Tag(name = "Storage Packages", description = "Gói dịch vụ lưu trữ cho exhibitor")
public class ExhibitorStorageUsageController extends BaseController {

    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;
    private final ExhibitorMediaAssetService mediaAssetService;

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('EXHIBITOR')")
    @RequireActiveCompany(roles = Role.EXHIBITOR)
    @Operation(summary = "Dung lượng lưu trữ hiện tại", description = "Trả về số byte đã dùng, tổng quota và % sử dụng.")
    public ResponseEntity<ApiResponse<StorageUsageResponseDTO>> getUsage(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Company company = companyService.getCompanyEntityForCurrentUser(userDetails.getUser());
        long mediaAssetBytes = mediaAssetService.sumMediaAssetFileSizeByCompanyId(company.getId());
        return ok(companyStorageService.getUsage(company, mediaAssetBytes));
    }
}
