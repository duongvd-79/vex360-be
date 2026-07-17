package com.example.vex360.features.booth.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothTemplateHotspotService;
import com.example.vex360.features.booth.services.BoothTemplatePanoramaService;
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
    private final BoothTemplatePanoramaService boothTemplatePanoramaService;
    private final BoothTemplateHotspotService boothTemplateHotspotService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> createBoothTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateBoothTemplateRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            MultipartHttpServletRequest multipartRequest) {
        BoothTemplateResponseDTO template = boothTemplateService.createBoothTemplate(
                userDetails.getUser(),
                request,
                thumbnail,
                extractPanoramaFiles(multipartRequest));

        return created(template);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> updateBoothTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestPart(value = "metadata", required = false) UpdateBoothTemplateRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        BoothTemplateResponseDTO template = boothTemplateService.updateBoothTemplate(
                userDetails.getUser(),
                id,
                request,
                thumbnail);
        return ok(template);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<BoothTemplateSummaryResponseDTO>>> getBoothTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoothStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "name") Pageable pageable) {
        PageResponse<BoothTemplateSummaryResponseDTO> templates = boothTemplateService
                .getBoothTemplates(keyword, status, pageable);
        return ok(templates);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> getBoothTemplateById(@PathVariable UUID id) {
        BoothTemplateResponseDTO template = boothTemplateService.getBoothTemplateById(id);
        return ok(template);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<BoothTemplateResponseDTO>> deleteBoothTemplate(@PathVariable UUID id) {
        BoothTemplateResponseDTO template = boothTemplateService.deleteBoothTemplate(id);
        return ok(template);
    }

    // --- Admin Panorama Endpoints ---

    @GetMapping("/{boothId}/panoramas")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<PanoramaResponseDTO>>> getPanoramas(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        List<PanoramaResponseDTO> panoramas = boothTemplatePanoramaService.getPanoramas(
                userDetails.getUser(),
                boothId);
        return ok(panoramas);
    }


    @PostMapping(path = "/{boothId}/panoramas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> createPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @Valid @RequestPart("metadata") CreateExhibitorPanoramaRequest request,
            @RequestPart("image") MultipartFile image) {
        PanoramaResponseDTO panorama = boothTemplatePanoramaService.createPanorama(
                userDetails.getUser(),
                boothId,
                request,
                image);
        return created(panorama);
    }

    @PatchMapping(path = "/{boothId}/panoramas/{panoramaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> updatePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @Valid @RequestPart(value = "metadata", required = false) UpdateExhibitorPanoramaRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        PanoramaResponseDTO panorama = boothTemplatePanoramaService.updatePanorama(
                userDetails.getUser(),
                boothId,
                panoramaId,
                request,
                image);
        return ok(panorama);
    }

    @DeleteMapping("/{boothId}/panoramas/{panoramaId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PanoramaResponseDTO>> deletePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId) {
        PanoramaResponseDTO panorama = boothTemplatePanoramaService.deletePanorama(
                userDetails.getUser(),
                boothId,
                panoramaId);
        return ok(panorama);
    }

    // --- Admin Hotspot Endpoints ---

    @GetMapping("/{boothId}/panoramas/{panoramaId}/hotspots")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<HotspotResponseDTO>>> getHotspots(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId) {
        List<HotspotResponseDTO> hotspots = boothTemplateHotspotService.getHotspots(
                userDetails.getUser(),
                boothId,
                panoramaId);
        return ok(hotspots);
    }

    @PostMapping("/{boothId}/panoramas/{panoramaId}/hotspots")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> createHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @Valid @RequestBody CreateBoothTemplateHotspotRequest request) {
        HotspotResponseDTO hotspot = boothTemplateHotspotService.createHotspot(
                userDetails.getUser(),
                boothId,
                panoramaId,
                request);
        return created(hotspot);
    }

    @PatchMapping("/{boothId}/panoramas/{panoramaId}/hotspots/{hotspotId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> updateHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId,
            @Valid @RequestBody UpdateBoothTemplateHotspotRequest request) {
        HotspotResponseDTO hotspot = boothTemplateHotspotService.updateHotspot(
                userDetails.getUser(),
                boothId,
                panoramaId,
                hotspotId,
                request);
        return ok(hotspot);
    }

    @DeleteMapping("/{boothId}/panoramas/{panoramaId}/hotspots/{hotspotId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<HotspotResponseDTO>> deleteHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId) {
        HotspotResponseDTO hotspot = boothTemplateHotspotService.deleteHotspot(
                userDetails.getUser(),
                boothId,
                panoramaId,
                hotspotId);
        return ok(hotspot);
    }

    private Map<String, MultipartFile> extractPanoramaFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> files = new LinkedHashMap<>(request.getFileMap());
        files.remove("metadata");
        files.remove("thumbnail");
        return files;
    }
}
