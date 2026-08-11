package com.example.vex360.features.designrequest.controllers;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.ReorderDesignDraftPanoramasRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftMediaAssetRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftSettingsRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftAssetNameRequest;
import com.example.vex360.features.designrequest.dtos.request.UpsertDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftMediaAssetResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftHotspotResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPanoramaResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPreviewResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftSettingsResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerDesignRequestSummaryResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftPreviewSource;
import com.example.vex360.features.designrequest.services.DesignerDraftEditorService;
import com.example.vex360.features.designrequest.services.DesignerDraftPreviewService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/designer/design-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DESIGNER')")
@Tag(name = "Designer - Design Requests", description = "Designer quản lý các yêu cầu thiết kế được Admin phân công, truy cập dữ liệu booth của Exhibitor, dựng bản thiết kế hoàn chỉnh và gửi bản thảo để Exhibitor review.")
public class DesignerDesignRequestController extends BaseController {
    private final DesignRequestService designRequestService;
    private final DesignerWorkspaceService designerWorkspaceService;
    private final DesignDraftAssetService designDraftAssetService;
    private final DesignerDraftEditorService draftEditorService;
    private final DesignerDraftPreviewService draftPreviewService;

    @GetMapping
    @Operation(summary = "Xem danh sách yêu cầu thiết kế được phân công", description = "Trả về các design request được phân công cho Designer đang đăng nhập. Có thể lọc theo status và phân trang; mặc định sắp xếp theo thời gian tạo giảm dần.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForDesigner(userDetails.getUser(), status, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get assigned and revision-requested design request counts")
    public ResponseEntity<ApiResponse<DesignerDesignRequestSummaryResponseDTO>> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ok(designRequestService.getSummaryForDesigner(userDetails.getUser()));
    }

    @GetMapping("/{id}/workspace")
    @Operation(summary = "Xem workspace thiết kế booth", description = "Trả về toàn bộ dữ liệu cần thiết để Designer tiếp tục công việc: thông tin request, trạng thái review, booth hiện tại, working draft đang lưu và submitted draft gần nhất. Chỉ Designer được phân công cho request mới có quyền truy cập.")
    public ResponseEntity<ApiResponse<DesignerWorkspaceResponseDTO>> getWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designerWorkspaceService.getWorkspace(userDetails.getUser(), id));
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview khong gian booth 360 cua Designer", description = "Tra ve cung cau truc panorama va hotspot de frontend dung renderer 360 hien tai. Source co the la working draft, submitted draft moi nhat hoac booth chinh thuc.")
    public ResponseEntity<ApiResponse<DesignDraftPreviewResponseDTO>> getPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false) DesignDraftPreviewSource source) {
        return ok(draftPreviewService.getPreview(userDetails.getUser(), id, source));
    }

    @PutMapping("/{id}/working-draft/settings")
    @Operation(summary = "Cap nhat thong tin working draft", description = "Cap nhat ten, mo ta, template, thumbnail va background music cua working draft version 0.")
    public ResponseEntity<ApiResponse<DesignDraftSettingsResponseDTO>> updateDraftSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody UpdateDesignDraftSettingsRequest request) {
        return ok(draftEditorService.updateSettings(userDetails.getUser(), id, expectedRevision, request));
    }

    @PostMapping("/{id}/working-draft/panoramas")
    @Operation(summary = "Them khong gian 360 vao working draft")
    public ResponseEntity<ApiResponse<DesignDraftPanoramaResponseDTO>> createDraftPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody CreateDesignDraftPanoramaRequest request) {
        return created(draftEditorService.createPanorama(userDetails.getUser(), id, expectedRevision, request));
    }

    @PatchMapping("/{id}/working-draft/panoramas/{panoramaId}")
    @Operation(summary = "Cap nhat khong gian 360 trong working draft")
    public ResponseEntity<ApiResponse<DesignDraftPanoramaResponseDTO>> updateDraftPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID panoramaId,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody UpdateDesignDraftPanoramaRequest request) {
        return ok(draftEditorService.updatePanorama(userDetails.getUser(), id, panoramaId, expectedRevision, request));
    }

    @PutMapping("/{id}/working-draft/panoramas/order")
    @Operation(summary = "Sap xep lai cac khong gian 360 trong working draft")
    public ResponseEntity<ApiResponse<List<DesignDraftPanoramaResponseDTO>>> reorderDraftPanoramas(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody ReorderDesignDraftPanoramasRequest request) {
        return ok(draftEditorService.reorderPanoramas(userDetails.getUser(), id, expectedRevision, request));
    }

    @DeleteMapping("/{id}/working-draft/panoramas/{panoramaId}")
    @Operation(summary = "Xoa khong gian 360 khoi working draft")
    public ResponseEntity<ApiResponse<DesignDraftPanoramaResponseDTO>> deleteDraftPanorama(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @PathVariable UUID panoramaId) {
        return ok(draftEditorService.deletePanorama(userDetails.getUser(), id, expectedRevision, panoramaId));
    }

    @PostMapping("/{id}/working-draft/panoramas/{panoramaId}/hotspots")
    @Operation(summary = "Them hotspot vao khong gian 360 cua working draft")
    public ResponseEntity<ApiResponse<DesignDraftHotspotResponseDTO>> createDraftHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID panoramaId,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody UpsertDesignDraftHotspotRequest request) {
        return created(draftEditorService.createHotspot(userDetails.getUser(), id, panoramaId, expectedRevision, request));
    }

    @PatchMapping("/{id}/working-draft/panoramas/{panoramaId}/hotspots/{hotspotId}")
    @Operation(summary = "Cap nhat hotspot trong working draft")
    public ResponseEntity<ApiResponse<DesignDraftHotspotResponseDTO>> updateDraftHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID panoramaId,
            @PathVariable UUID hotspotId,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody UpsertDesignDraftHotspotRequest request) {
        return ok(draftEditorService.updateHotspot(
                userDetails.getUser(), id, panoramaId, hotspotId, expectedRevision, request));
    }

    @DeleteMapping("/{id}/working-draft/panoramas/{panoramaId}/hotspots/{hotspotId}")
    @Operation(summary = "Xoa hotspot khoi working draft")
    public ResponseEntity<ApiResponse<DesignDraftHotspotResponseDTO>> deleteDraftHotspot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID panoramaId,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @PathVariable UUID hotspotId) {
        return ok(draftEditorService.deleteHotspot(
                userDetails.getUser(), id, panoramaId, expectedRevision, hotspotId));
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "Xem sản phẩm được phép dùng trong yêu cầu thiết kế", description = "Trả về các product ACTIVE thuộc allowlist bất biến của design request. Hỗ trợ tìm kiếm theo keyword, lọc category và phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponseDTO>>> getProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designerWorkspaceService.getProducts(
                userDetails.getUser(), id, keyword, categoryId, pageable));
    }

    @GetMapping("/{id}/media-assets")
    @Operation(summary = "Xem media asset được phép dùng trong yêu cầu thiết kế", description = "Trả về MediaAsset thuộc allowlist bất biến của design request, hỗ trợ filterType=IMAGE|VIDEO|all và phân trang. Staging media do Designer upload được quản lý riêng.")
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetResponseDTO>>> getMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false) String filterType,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designerWorkspaceService.getMediaAssets(userDetails.getUser(), id, filterType, pageable));
    }

    @PostMapping(value = "/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /*
     * @Operation(summary = "Upload panorama cho bản thiết kế", description =
     * "Upload một ảnh panorama staging thuộc design request đang ở trạng thái ASSIGNED hoặc REVISION_REQUESTED. Chấp nhận JPEG, PNG hoặc WEBP tối đa 10 MB; dung lượng được tính vào quota của company Exhibitor. Response trả về assetId, URL và imageKey để dùng khi lưu draft."
     * )
     */
    @Operation(summary = "Upload staging asset", description = "Uploads an asset for an ASSIGNED or REVISION_REQUESTED design request. MEDIA_ATTACHMENT accepts JPEG, PNG, or MP4 up to 10 MB. PANORAMA and MEDIA_ATTACHMENT remain staged without changing quota until approval.")
    public ResponseEntity<ApiResponse<DesignDraftAssetResponseDTO>> uploadAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "PANORAMA") DesignDraftAssetType assetType,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return created(designDraftAssetService.uploadAsset(userDetails.getUser(), id, file, assetType));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @Operation(summary = "Xóa file staging đã upload", description = "Xóa DesignDraftAsset khỏi design request. Với MEDIA_ATTACHMENT chưa được hotspot sử dụng, backend tự gỡ quan hệ khỏi working draft trước khi xóa. Từ chối nếu asset vẫn được hotspot, submitted draft hoặc booth tham chiếu.")
    public ResponseEntity<ApiResponse<DesignDraftAssetResponseDTO>> deleteAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @PathVariable UUID assetId) {
        return ok(designDraftAssetService.releaseAsset(userDetails.getUser(), id, expectedRevision, assetId));
    }

    @PatchMapping("/{id}/assets/{assetId}/name")
    @Operation(summary = "Đổi tên media staging", description = "Đổi tên hiển thị của MEDIA_ATTACHMENT trong working draft.")
    public ResponseEntity<ApiResponse<DesignDraftAssetResponseDTO>> renameAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID assetId,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody UpdateDesignDraftAssetNameRequest request) {
        return ok(designDraftAssetService.renameAsset(
                userDetails.getUser(), id, assetId, expectedRevision, request.getFileName()));
    }

    @GetMapping("/{id}/assets")
    @Operation(summary = "Xem danh sách asset của bản thiết kế", description = "Trả về các staging asset đã upload cho design request được phân công, có phân trang và mặc định sắp xếp theo thời gian tạo giảm dần. Danh sách này cung cấp assetId, URL và imageKey để Designer quản lý hoặc đưa vào working draft.")
    public ResponseEntity<ApiResponse<PageResponse<DesignDraftAssetResponseDTO>>> getAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designDraftAssetService.getAssets(userDetails.getUser(), id, pageable));
    }

    @Deprecated(since = "designer granular draft API")
    @PutMapping("/{id}/working-draft")
    @Operation(summary = "Lưu working draft đang thiết kế", description = "Tạo mới hoặc thay thế working draft mutable của request với version 0 mà chưa gửi cho Exhibitor. Dùng cho autosave khi request ở ASSIGNED hoặc REVISION_REQUESTED. Các staging asset không còn được working draft hay submitted draft tham chiếu sẽ được tự động dọn dẹp và hoàn quota.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> saveWorkingDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody SubmitDesignDraftRequest request) {
        return ok(designRequestService.saveWorkingDraft(userDetails.getUser(), id, expectedRevision, request));
    }

    @PostMapping("/{id}/working-draft/submit")
    @Operation(summary = "Gửi working draft để Exhibitor review", description = "Đóng working draft version 0 thành submitted draft bất biến với version kế tiếp và chuyển request sang DRAFT_SUBMITTED. Request phải có working draft hợp lệ, đang ở ASSIGNED hoặc REVISION_REQUESTED và có thay đổi thiết kế so với submitted version gần nhất; nếu không thay đổi, API trả DESIGN-044. Sau khi gửi, Designer không thể chỉnh sửa cho đến khi Exhibitor yêu cầu revision.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> submitWorkingDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designRequestService.submitWorkingDraft(userDetails.getUser(), id));
    }

    @PostMapping("/{id}/working-draft/media-assets")
    /*
     * @Operation(summary = "Thêm media asset đính kèm vào working draft",
     * description =
     * "Thêm một tệp media (ảnh, video, audio, PDF, file 3D) đã upload làm media asset đính kèm cho draft."
     * )
     */
    @Operation(summary = "Add review media to the working draft", description = "Adds an uploaded MEDIA_ATTACHMENT to the review list. Only JPEG, PNG, and MP4 are supported; audio, PDF, and 3D models are outside the current scope.")
    public ResponseEntity<ApiResponse<DesignDraftMediaAssetResponseDTO>> addMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @Valid @RequestBody SubmitDesignDraftMediaAssetRequest request) {
        return created(draftEditorService.addMediaAsset(userDetails.getUser(), id, expectedRevision, request));
    }

    @DeleteMapping("/{id}/working-draft/media-assets/{mediaAssetId}")
    @Operation(summary = "Gỡ media khỏi working draft", description = "Chỉ xóa DesignDraftMediaAsset khỏi working draft và giữ lại DesignDraftAsset staging để Designer có thể thêm lại. Từ chối nếu hotspot đang tham chiếu media.")
    public ResponseEntity<ApiResponse<Void>> removeMediaAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @PathVariable UUID mediaAssetId) {
        draftEditorService.removeMediaAsset(userDetails.getUser(), id, expectedRevision, mediaAssetId);
        return ok(null);
    }

    @PutMapping("/{id}/working-draft/media-assets/reorder")
    @Operation(summary = "Sắp xếp danh sách media asset đính kèm trong working draft", description = "Cập nhật thứ tự hiển thị danh sách media asset đính kèm.")
    public ResponseEntity<ApiResponse<List<DesignDraftMediaAssetResponseDTO>>> reorderMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestHeader("X-Draft-Revision") long expectedRevision,
            @RequestBody List<UUID> orderedMediaAssetIds) {
        return ok(draftEditorService.reorderMediaAssets(
                userDetails.getUser(), id, expectedRevision, orderedMediaAssetIds));
    }
}
