package com.example.vex360.features.exhibition.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.packagetemplate.dtos.request.CreatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/packages/templates")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin Exhibition Package Templates", description = "Quản lý các template gói dịch vụ triển lãm dành cho Admin")
public class AdminExhibitionPackageController extends BaseController {

    private final PackageTemplateService packageTemplateService;

    @PostMapping
    @Operation(summary = "Định nghĩa gói mẫu mới", description = "Tạo một mẫu gói đăng ký triển lãm mới với các giới hạn tài nguyên và giá sàn mặc định.")
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> createPackageTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreatePackageTemplateRequest request) {
        PackageTemplateResponseDTO template = packageTemplateService
                .createPackageTemplate(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(template));
    }
}
