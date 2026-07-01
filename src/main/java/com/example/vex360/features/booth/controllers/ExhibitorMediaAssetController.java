package com.example.vex360.features.booth.controllers;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.CreateMediaAssetRequest;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/media-assets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
public class ExhibitorMediaAssetController extends BaseController {
    private final ExhibitorMediaAssetService exhibitorMediaAssetService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetResponseDTO>>> getMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(exhibitorMediaAssetService.getMediaAssets(userDetails.getUser(), pageable));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> createMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateMediaAssetRequest request,
            @RequestPart("file") MultipartFile file) {
        return created(exhibitorMediaAssetService.createMediaAsset(userDetails.getUser(), request, file));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> deleteMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assetId) {
        return ok(exhibitorMediaAssetService.deleteMediaAsset(userDetails.getUser(), assetId));
    }
}
