package com.example.vex360.features.booth.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/booth-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
public class ExhibitorBoothTemplateController extends BaseController {
    private final ExhibitorBoothTemplateService exhibitorBoothTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ExhibitorBoothTemplateSummaryResponseDTO>>> getPublishedTemplates(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "name",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ok(exhibitorBoothTemplateService.getPublishedTemplates(keyword, pageable));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<ExhibitorBoothTemplateResponseDTO>> getPublishedTemplate(
            @PathVariable UUID templateId) {
        return ok(exhibitorBoothTemplateService.getPublishedTemplate(templateId));
    }
}
