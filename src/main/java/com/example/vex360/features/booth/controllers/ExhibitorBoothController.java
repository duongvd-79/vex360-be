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
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/booths")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
public class ExhibitorBoothController extends BaseController {
    private final ExhibitorBoothService exhibitorBoothService;
    private final ExhibitorPanoramaService exhibitorPanoramaService;
    private final ExhibitorHotspotService exhibitorHotspotService;

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

    @PatchMapping(path = "/{boothId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BoothResponseDTO>> updateBooth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @Valid @RequestPart(value = "metadata", required = false) UpdateBoothRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        BoothResponseDTO booth = exhibitorBoothService.updateBooth(
                userDetails.getUser(),
                boothId,
                request,
                thumbnail);
        return ok(booth);
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
            @RequestPart("image") MultipartFile image) {
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
