package com.example.vex360.features.designrequest.controllers;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequestMessageRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestMessageResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestCommunicationService;
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
    private final DesignRequestCommunicationService communicationService;

    @GetMapping
    @Operation(summary = "Xem danh sách yêu cầu thiết kế được phân công", description = "Trả về các design request được phân công cho Designer đang đăng nhập. Có thể lọc theo status và phân trang; mặc định sắp xếp theo thời gian tạo giảm dần.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForDesigner(userDetails.getUser(), status, pageable));
    }

    @GetMapping("/{id}/workspace")
    @Operation(summary = "Xem workspace thiết kế booth", description = "Trả về toàn bộ dữ liệu cần thiết để Designer tiếp tục công việc: thông tin request, trạng thái review, booth hiện tại, working draft đang lưu và submitted draft gần nhất. Chỉ Designer được phân công cho request mới có quyền truy cập.")
    public ResponseEntity<ApiResponse<DesignerWorkspaceResponseDTO>> getWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designerWorkspaceService.getWorkspace(userDetails.getUser(), id));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Designer xem tin nhắn trao đổi làm rõ", description = "Trả về lịch sử các tin nhắn trao đổi làm rõ đối với yêu cầu thiết kế được phân công.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestMessageResponseDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt") Pageable pageable) {
        return ok(communicationService.getForDesigner(userDetails.getUser(), id, pageable));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Designer gửi tin nhắn trao đổi làm rõ", description = "Gửi tin nhắn trao đổi làm rõ đến Exhibitor liên quan đến bản thiết kế.")
    public ResponseEntity<ApiResponse<DesignRequestMessageResponseDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody CreateDesignRequestMessageRequest request) {
        return created(communicationService.sendForDesigner(userDetails.getUser(), id, request.getMessage()));
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "Xem sản phẩm của Exhibitor để gắn vào booth", description = "Trả về các product ACTIVE thuộc company sở hữu booth của design request. Hỗ trợ tìm kiếm theo keyword, lọc category và phân trang. Designer chỉ được tham chiếu các product này trong hotspot, không được tạo hoặc chỉnh sửa product thay Exhibitor.")
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
    @Operation(summary = "Xem media asset của Exhibitor để gắn vào booth", description = "Trả về danh sách media asset thuộc company sở hữu booth của design request, có phân trang. Designer chỉ được dùng các asset này làm nội dung hotspot và không có quyền thay đổi dữ liệu gốc của Exhibitor.")
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetResponseDTO>>> getMediaAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designerWorkspaceService.getMediaAssets(userDetails.getUser(), id, pageable));
    }

    @PostMapping(value = "/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload panorama cho bản thiết kế", description = "Upload một ảnh panorama staging thuộc design request đang ở trạng thái ASSIGNED hoặc REVISION_REQUESTED. Chấp nhận JPEG, PNG hoặc WEBP tối đa 10 MB; dung lượng được tính vào quota của company Exhibitor. Response trả về assetId, URL và imageKey để dùng khi lưu draft.")
    public ResponseEntity<ApiResponse<DesignDraftAssetResponseDTO>> uploadAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "PANORAMA") DesignDraftAssetType assetType,
            @RequestPart("file") MultipartFile file) {
        return created(designDraftAssetService.uploadAsset(userDetails.getUser(), id, file, assetType));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @Operation(summary = "Xóa asset thiết kế chưa được sử dụng", description = "Xóa một staging asset thuộc design request và hoàn lại dung lượng cho company Exhibitor. Chỉ cho phép khi request còn có thể chỉnh sửa và asset chưa được tham chiếu bởi working draft, submitted draft hoặc booth hiện tại.")
    public ResponseEntity<ApiResponse<DesignDraftAssetResponseDTO>> deleteAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID assetId) {
        return ok(designDraftAssetService.releaseAsset(userDetails.getUser(), id, assetId));
    }

    @GetMapping("/{id}/assets")
    @Operation(summary = "Xem danh sách asset của bản thiết kế", description = "Trả về các staging asset đã upload cho design request được phân công, có phân trang và mặc định sắp xếp theo thời gian tạo giảm dần. Danh sách này cung cấp assetId, URL và imageKey để Designer quản lý hoặc đưa vào working draft.")
    public ResponseEntity<ApiResponse<PageResponse<DesignDraftAssetResponseDTO>>> getAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designDraftAssetService.getAssets(userDetails.getUser(), id, pageable));
    }


    @PutMapping("/{id}/working-draft")
    @Operation(summary = "Lưu working draft đang thiết kế", description = "Tạo mới hoặc thay thế working draft mutable của request với version 0 mà chưa gửi cho Exhibitor. Dùng cho autosave khi request ở ASSIGNED hoặc REVISION_REQUESTED. Các staging asset không còn được working draft hay submitted draft tham chiếu sẽ được tự động dọn dẹp và hoàn quota.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> saveWorkingDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitDesignDraftRequest request) {
        return ok(designRequestService.saveWorkingDraft(userDetails.getUser(), id, request));
    }

    @PostMapping("/{id}/working-draft/submit")
    @Operation(summary = "Gửi working draft để Exhibitor review", description = "Đóng working draft version 0 thành submitted draft bất biến với version kế tiếp và chuyển request sang DRAFT_SUBMITTED. Request phải có working draft hợp lệ và đang ở ASSIGNED hoặc REVISION_REQUESTED; sau khi gửi, Designer không thể chỉnh sửa cho đến khi Exhibitor yêu cầu revision.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> submitWorkingDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designRequestService.submitWorkingDraft(userDetails.getUser(), id));
    }
}
