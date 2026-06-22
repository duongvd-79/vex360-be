package com.example.vex360.features.company.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Company Profile", description = "Quản lý hồ sơ công ty của user đang đăng nhập")
public class CompanyController {
    private final CompanyService companyService;

    @GetMapping("/me")
    @Operation(
            summary = "Lấy công ty của tài khoản hiện tại",
            description = "Trả về công ty đang thuộc sở hữu của user đăng nhập. Nếu user chưa có company, API trả lỗi COMPANY_NOT_FOUND.")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> getCurrentUserCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CompanyResponseDTO company = companyService.getCurrentUserCompany(userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(company));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Cập nhật hồ sơ công ty",
            description = "Cập nhật các trường profile còn thiếu. Khi industry, description, logoUrl, website, phone và address đều có dữ liệu, company sẽ chuyển sang ACTIVE.")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> updateCurrentUserCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateCompanyProfileRequest request) {
        CompanyResponseDTO company = companyService.updateCurrentUserCompany(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success(company));
    }
}
