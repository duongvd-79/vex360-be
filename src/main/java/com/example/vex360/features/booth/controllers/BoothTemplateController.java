package com.example.vex360.features.booth.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothTemplateService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/booths/templates")
@RequiredArgsConstructor
public class BoothTemplateController extends BaseController {
    private final BoothTemplateService boothTemplateService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> createBoothTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateBoothTemplateRequest request,
            MultipartHttpServletRequest multipartRequest) {
        BoothTemplateResponseDTO template = boothTemplateService.createBoothTemplate(
                userDetails.getUser(),
                request,
                extractPanoramaFiles(multipartRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(template));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<BoothTemplateSummaryResponseDTO>>> getBoothTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoothStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "name") Pageable pageable) {
        PageResponse<BoothTemplateSummaryResponseDTO> templates = boothTemplateService
                .getBoothTemplates(keyword, status, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(templates));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> getBoothTemplateById(@PathVariable UUID id) {
        BoothTemplateResponseDTO template = boothTemplateService.getBoothTemplateById(id);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(template));
    }

    private Map<String, MultipartFile> extractPanoramaFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> files = new LinkedHashMap<>(request.getFileMap());
        files.remove("metadata");
        return files;
    }
}
