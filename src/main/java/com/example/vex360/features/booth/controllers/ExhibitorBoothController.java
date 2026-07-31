package com.example.vex360.features.booth.controllers;

import java.util.UUID;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/booths")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
public class ExhibitorBoothController extends BaseController {
    private static final String AUTO_APPROVED_NO_CHANGES_MESSAGE =
            "Gian hàng không có thay đổi so với phiên bản đã duyệt gần nhất. "
                    + "Hệ thống đã giữ nguyên trạng thái đã duyệt và không tạo yêu cầu xét duyệt mới.";

    private final ExhibitorBoothService exhibitorBoothService;
    private final ExhibitorBoothTemplateService exhibitorBoothTemplateService;
    private final ExhibitorPanoramaService exhibitorPanoramaService;
    private final ExhibitorHotspotService exhibitorHotspotService;
    private final BoothReviewService boothReviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getBooths(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<BoothResponseDTO> booths = exhibitorBoothService.getBooths(userDetails.getUser(), pageable);
        return ok(booths);
    }

    @GetMapping("/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> getBoothById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        BoothResponseDTO booth = exhibitorBoothService.getBoothById(userDetails.getUser(), boothId);
        return ok(booth);
    }

    @GetMapping("/{boothId}/templates")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitorBoothTemplateSummaryResponseDTO>>> getCompatibleTemplates(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "name",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ok(exhibitorBoothTemplateService.getCompatibleTemplates(
                userDetails.getUser(), boothId, keyword, pageable));
    }

    @GetMapping("/{boothId}/templates/{templateId}")
    public ResponseEntity<ApiResponse<ExhibitorBoothTemplateResponseDTO>> getCompatibleTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID templateId) {
        return ok(exhibitorBoothTemplateService.getCompatibleTemplate(
                userDetails.getUser(), boothId, templateId));
    }

    @PostMapping("/{boothId}/templates/{templateId}/apply")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> applyTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID templateId) {
        return ok(exhibitorBoothTemplateService.applyTemplate(
                userDetails.getUser(), boothId, templateId));
    }

    @PatchMapping(path = "/{boothId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BoothResponseDTO>> updateBooth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @Valid @RequestPart(value = "metadata", required = false) UpdateBoothRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestPart(value = "backgroundMusic", required = false) MultipartFile backgroundMusic) {
        BoothResponseDTO booth = exhibitorBoothService.updateBooth(
                userDetails.getUser(),
                boothId,
                request,
                thumbnail,
                backgroundMusic);
        return ok(booth);
    }

    @DeleteMapping("/{boothId}/background-music")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> deleteBackgroundMusic(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        return ok(exhibitorBoothService.deleteBackgroundMusic(userDetails.getUser(), boothId));
    }

    @PutMapping("/{boothId}/start-edit")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> startEdit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        return ok(boothReviewService.startEdit(userDetails.getUser(), boothId));
    }

    @PutMapping("/{boothId}/submit-review")
    public ResponseEntity<ApiResponse<BoothReviewRequestSummaryDTO>> submitReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        BoothReviewRequestSummaryDTO summary = boothReviewService.submitReview(userDetails.getUser(), boothId);
        if (summary.getStatus() == BoothReviewStatus.APPROVED) {
            return ok(summary, AUTO_APPROVED_NO_CHANGES_MESSAGE);
        }
        return ok(summary);
    }

    @GetMapping("/{boothId}/review-requests")
    public ResponseEntity<ApiResponse<PageResponse<BoothReviewRequestSummaryDTO>>> getReviewRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(boothReviewService.getReviewHistory(userDetails.getUser(), boothId, pageable));
    }

    @GetMapping("/{boothId}/panoramas")
    public ResponseEntity<ApiResponse<List<PanoramaResponseDTO>>> getPanoramas(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        return ok(exhibitorPanoramaService.getPanoramas(userDetails.getUser(), boothId));
    }

    @GetMapping("/{boothId}/panoramas/{panoramaId}")
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> getPanoramaById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId) {
        return ok(exhibitorPanoramaService.getPanoramaById(userDetails.getUser(), boothId, panoramaId));
    }

    @PostMapping(path = "/{boothId}/panoramas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> createPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @Valid @RequestPart("metadata") CreateExhibitorPanoramaRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return created(exhibitorPanoramaService.createPanorama(userDetails.getUser(), boothId, request, image));
    }

    @PatchMapping(path = "/{boothId}/panoramas/{panoramaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> updatePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @Valid @RequestPart(value = "metadata", required = false) UpdateExhibitorPanoramaRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ok(exhibitorPanoramaService.updatePanorama(userDetails.getUser(), boothId, panoramaId, request, image));
    }

    @DeleteMapping("/{boothId}/panoramas/{panoramaId}")
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> deletePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId) {
        return ok(exhibitorPanoramaService.deletePanorama(userDetails.getUser(), boothId, panoramaId));
    }

    @DeleteMapping("/{boothId}/panoramas")
    public ResponseEntity<ApiResponse<Void>> deleteAllPanoramas(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        exhibitorPanoramaService.deleteAllPanoramas(userDetails.getUser(), boothId);
        return ok(null);
    }

    @GetMapping("/{boothId}/panoramas/{panoramaId}/hotspots")
    public ResponseEntity<ApiResponse<List<HotspotResponseDTO>>> getHotspots(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId) {
        return ok(exhibitorHotspotService.getHotspots(userDetails.getUser(), boothId, panoramaId));
    }

    @PostMapping("/{boothId}/panoramas/{panoramaId}/hotspots")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> createHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @Valid @RequestBody UpsertHotspotRequest request) {
        return created(exhibitorHotspotService.createHotspot(userDetails.getUser(), boothId, panoramaId, request));
    }

    @PatchMapping("/{boothId}/panoramas/{panoramaId}/hotspots/{hotspotId}")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> updateHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId,
            @Valid @RequestBody UpsertHotspotRequest request) {
        return ok(exhibitorHotspotService.updateHotspot(
                userDetails.getUser(),
                boothId,
                panoramaId,
                hotspotId,
                request));
    }

    @DeleteMapping("/{boothId}/panoramas/{panoramaId}/hotspots/{hotspotId}")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> deleteHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId) {
        return ok(exhibitorHotspotService.deleteHotspot(userDetails.getUser(), boothId, panoramaId, hotspotId));
    }
}
