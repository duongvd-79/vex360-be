package com.example.vex360.features.packagetemplate.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/package-templates")
@RequiredArgsConstructor
public class PackageTemplateController extends BaseController {
    private final PackageTemplateService packageTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageTemplateResponseDTO>>> getActivePackageTemplates() {
        List<PackageTemplateResponseDTO> templates = packageTemplateService.getActivePackageTemplates();
        return ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageTemplateResponseDTO>> getActivePackageTemplateById(@PathVariable UUID id) {
        PackageTemplateResponseDTO template = packageTemplateService.getActivePackageTemplateById(id);
        return ok(template);
    }
}
