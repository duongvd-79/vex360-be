package com.example.vex360.features.packagetemplate.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.packagetemplate.dtos.request.CreatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateStatusRequest;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.PackageTemplateStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/package-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminPackageTemplateController extends BaseController {
    private final PackageTemplateService packageTemplateService;

    @PostMapping
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> createPackageTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreatePackageTemplateRequest request) {
        PackageTemplateResponseDTO template = packageTemplateService
                .createPackageTemplate(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(template));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PackageTemplateResponseDTO>>> getPackageTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PackageTemplateStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "name") Pageable pageable) {
        PageResponse<PackageTemplateResponseDTO> templates = packageTemplateService
                .getPackageTemplates(keyword, status, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(templates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> getPackageTemplateById(@PathVariable UUID id) {
        PackageTemplateResponseDTO template = packageTemplateService.getPackageTemplateById(id);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(template));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> updatePackageTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePackageTemplateRequest request) {
        PackageTemplateResponseDTO template = packageTemplateService.updatePackageTemplate(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(template));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> updatePackageTemplateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePackageTemplateStatusRequest request) {
        PackageTemplateResponseDTO template = packageTemplateService.updatePackageTemplateStatus(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(template));
    }

}
