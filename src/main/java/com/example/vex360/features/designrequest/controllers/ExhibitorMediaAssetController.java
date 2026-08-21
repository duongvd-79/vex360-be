package com.example.vex360.features.designrequest.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.CreateMediaAssetRequest;
import com.example.vex360.features.booth.dtos.request.RenameMediaAssetRequest;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/media-assets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
public class ExhibitorMediaAssetController extends BaseController {

    private final ExhibitorMediaAssetService exhibitorMediaAssetService;
    private final DesignAssetReferenceService assetReferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetResponseDTO>>> getMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String filterType,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(exhibitorMediaAssetService.getMediaAssets(userDetails.getUser(), filterType, pageable));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> createMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateMediaAssetRequest request,
            @RequestPart("file") MultipartFile file) {
        return created(exhibitorMediaAssetService.createMediaAsset(userDetails.getUser(), request, file));
    }

    @PatchMapping("/{assetId}")
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> renameAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assetId,
            @Valid @RequestBody RenameMediaAssetRequest request) {
        return ok(exhibitorMediaAssetService.renameAsset(userDetails.getUser(), assetId, request));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> deleteMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assetId) {
        return ok(assetReferenceService.deleteMediaAsset(userDetails.getUser(), assetId));
    }
}
