package com.example.vex360.features.hall.controllers;

import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
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
import com.example.vex360.features.hall.dtos.request.CreateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.request.CreateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.request.UpdateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.request.UpdateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.request.UpsertHallHotspotRequest;
import com.example.vex360.features.hall.dtos.request.UpsertHallItemRequest;
import com.example.vex360.features.hall.dtos.response.ExhibitionHallResponseDTO;
import com.example.vex360.features.hall.dtos.response.HallHotspotResponseDTO;
import com.example.vex360.features.hall.dtos.response.HallItemResponseDTO;
import com.example.vex360.features.hall.dtos.response.HallPanoramaResponseDTO;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallHotspotService;
import com.example.vex360.features.hall.services.OrganizerHallItemService;
import com.example.vex360.features.hall.services.OrganizerHallMediaAssetService;
import com.example.vex360.features.hall.services.OrganizerHallPanoramaService;
import com.example.vex360.features.hall.services.HallReviewSnapshot;
import com.example.vex360.features.hall.services.OrganizerHallPreviewService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer/exhibitions/{exhibitionUuid}/hall")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
@RequireActiveCompany(roles = Role.ORGANIZER)
@Tag(name = "Organizer Exhibition Hall", description = "Quản lý metadata sảnh triển lãm")
public class OrganizerHallController extends BaseController {
    private final ExhibitionHallService hallService;
    private final OrganizerHallPanoramaService panoramaService;
    private final OrganizerHallHotspotService hotspotService;
    private final OrganizerHallItemService itemService;
    private final OrganizerHallMediaAssetService mediaAssetService;
    private final OrganizerHallPreviewService previewService;

    @PostMapping
    @Operation(summary = "Tạo sảnh triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> createHall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @Valid @RequestBody CreateExhibitionHallRequest request) {
        return created(hallService.createHall(userDetails.getUser(), exhibitionUuid, request));
    }

    @GetMapping
    @Operation(summary = "Xem sảnh triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> getHall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return ok(hallService.getHall(userDetails.getUser(), exhibitionUuid));
    }


    @GetMapping("/preview")
    @Operation(summary = "Preview current hall draft")
    public ResponseEntity<ApiResponse<HallReviewSnapshot>> getPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return ok(previewService.getPreview(userDetails.getUser(), exhibitionUuid));
    }

    @PutMapping
    @Operation(summary = "Cập nhật metadata sảnh triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> updateHall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @Valid @RequestBody UpdateExhibitionHallRequest request) {
        return ok(hallService.updateHall(userDetails.getUser(), exhibitionUuid, request));
    }

    @PutMapping(path = "/background-music", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Thêm hoặc thay nhạc nền sảnh triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> updateBackgroundMusic(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @RequestPart("backgroundMusic") MultipartFile backgroundMusic) {
        return ok(hallService.updateBackgroundMusic(
                userDetails.getUser(), exhibitionUuid, backgroundMusic));
    }

    @DeleteMapping("/background-music")
    @Operation(summary = "Xóa nhạc nền sảnh triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> deleteBackgroundMusic(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return ok(hallService.deleteBackgroundMusic(userDetails.getUser(), exhibitionUuid));
    }

    @GetMapping("/panoramas")
    public ResponseEntity<ApiResponse<List<HallPanoramaResponseDTO>>> getPanoramas(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return ok(panoramaService.getPanoramas(userDetails.getUser(), exhibitionUuid));
    }

    @GetMapping("/panoramas/{panoramaId}")
    public ResponseEntity<ApiResponse<HallPanoramaResponseDTO>> getPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId) {
        return ok(panoramaService.getPanorama(userDetails.getUser(), exhibitionUuid, panoramaId));
    }

    @PostMapping(path = "/panoramas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HallPanoramaResponseDTO>> createPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @Valid @RequestPart("metadata") CreateHallPanoramaRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return created(panoramaService.createPanorama(
                userDetails.getUser(), exhibitionUuid, request, image));
    }

    @PutMapping(path = "/panoramas/{panoramaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HallPanoramaResponseDTO>> updatePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId,
            @Valid @RequestPart(value = "metadata", required = false) UpdateHallPanoramaRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ok(panoramaService.updatePanorama(
                userDetails.getUser(), exhibitionUuid, panoramaId, request, image));
    }

    @DeleteMapping("/panoramas/{panoramaId}")
    public ResponseEntity<ApiResponse<HallPanoramaResponseDTO>> deletePanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId) {
        return ok(panoramaService.deletePanorama(userDetails.getUser(), exhibitionUuid, panoramaId));
    }

    @GetMapping("/panoramas/{panoramaId}/hotspots")
    public ResponseEntity<ApiResponse<List<HallHotspotResponseDTO>>> getHotspots(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId) {
        return ok(hotspotService.getHotspots(userDetails.getUser(), exhibitionUuid, panoramaId));
    }

    @GetMapping("/panoramas/{panoramaId}/hotspots/{hotspotId}")
    public ResponseEntity<ApiResponse<HallHotspotResponseDTO>> getHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId) {
        return ok(hotspotService.getHotspot(
                userDetails.getUser(), exhibitionUuid, panoramaId, hotspotId));
    }

    @PostMapping("/panoramas/{panoramaId}/hotspots")
    public ResponseEntity<ApiResponse<HallHotspotResponseDTO>> createHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId,
            @Valid @RequestBody UpsertHallHotspotRequest request) {
        return created(hotspotService.createHotspot(
                userDetails.getUser(), exhibitionUuid, panoramaId, request));
    }

    @PutMapping("/panoramas/{panoramaId}/hotspots/{hotspotId}")
    public ResponseEntity<ApiResponse<HallHotspotResponseDTO>> updateHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId,
            @Valid @RequestBody UpsertHallHotspotRequest request) {
        return ok(hotspotService.updateHotspot(
                userDetails.getUser(), exhibitionUuid, panoramaId, hotspotId, request));
    }

    @DeleteMapping("/panoramas/{panoramaId}/hotspots/{hotspotId}")
    public ResponseEntity<ApiResponse<HallHotspotResponseDTO>> deleteHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId) {
        return ok(hotspotService.deleteHotspot(
                userDetails.getUser(), exhibitionUuid, panoramaId, hotspotId));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<HallItemResponseDTO>>> getItems(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return ok(itemService.getItems(userDetails.getUser(), exhibitionUuid));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<HallItemResponseDTO>> getItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID itemId) {
        return ok(itemService.getItem(userDetails.getUser(), exhibitionUuid, itemId));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<HallItemResponseDTO>> createItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @Valid @RequestBody UpsertHallItemRequest request) {
        return created(itemService.createItem(userDetails.getUser(), exhibitionUuid, request));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<HallItemResponseDTO>> updateItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpsertHallItemRequest request) {
        return ok(itemService.updateItem(userDetails.getUser(), exhibitionUuid, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<HallItemResponseDTO>> deleteItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID itemId) {
        return ok(itemService.deleteItem(userDetails.getUser(), exhibitionUuid, itemId));
    }

    @GetMapping("/media-assets")
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetResponseDTO>>> getMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @RequestParam(required = false) String filterType,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(mediaAssetService.getMediaAssets(
                userDetails.getUser(), exhibitionUuid, filterType, pageable));
    }

    @PostMapping(path = "/media-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> createMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @Valid @RequestPart("metadata") CreateMediaAssetRequest request,
            @RequestPart("file") MultipartFile file) {
        return created(mediaAssetService.createMediaAsset(
                userDetails.getUser(), exhibitionUuid, request, file));
    }

    @PutMapping("/media-assets/{assetId}")
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> renameMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID assetId,
            @Valid @RequestBody RenameMediaAssetRequest request) {
        return ok(mediaAssetService.renameMediaAsset(
                userDetails.getUser(), exhibitionUuid, assetId, request));
    }

    @DeleteMapping("/media-assets/{assetId}")
    public ResponseEntity<ApiResponse<MediaAssetResponseDTO>> deleteMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID assetId) {
        return ok(mediaAssetService.deleteMediaAsset(
                userDetails.getUser(), exhibitionUuid, assetId));
    }
}
