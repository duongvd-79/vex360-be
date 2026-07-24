package com.example.vex360.features.designrequest.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.features.designrequest.dtos.request.ApproveDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.RejectDesignDraftRequest;

import com.example.vex360.features.designrequest.dtos.request.UpdateDesignRequestProductsRequest;
import com.example.vex360.features.designrequest.dtos.request.RequestDesignCancellationRequest;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequestMessageRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestMessageResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestCommunicationService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.designrequest.dtos.response.ExhibitorDesignReviewWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/design-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
@Tag(name = "Exhibitor - Design Requests", description = "Exhibitor gửi và review yêu cầu thiết kế booth")
public class ExhibitorDesignRequestController extends BaseController {
    private final DesignRequestService designRequestService;
    private final DesignRequestCommunicationService communicationService;
    private final DesignerWorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "Exhibitor tạo yêu cầu thiết kế booth", description = "Tạo một yêu cầu thiết kế booth mới, kiểm tra tính hợp lệ về gói triển lãm và trạng thái thanh toán trước khi cho phép tạo.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> createRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateDesignRequest request) {
        return created(designRequestService.createRequest(userDetails.getUser(), request));
    }

    @GetMapping
    @Operation(summary = "Exhibitor xem danh sách yêu cầu thiết kế booth", description = "Lấy danh sách các yêu cầu thiết kế booth của Exhibitor hiện tại, có hỗ trợ lọc theo trạng thái và phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForExhibitor(userDetails.getUser(), status, pageable));
    }

    @GetMapping("/eligibility/{boothId}")
    @Operation(summary = "Kiểm tra điều kiện tạo yêu cầu thiết kế của booth", description = "Kiểm tra xem booth được chỉ định có đủ điều kiện để tạo yêu cầu thiết kế hay không (chưa có request hoạt động, còn quota thiết kế, v.v.).")
    public ResponseEntity<ApiResponse<DesignRequestEligibilityResponseDTO>> getEligibility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        return ok(designRequestService.getEligibility(userDetails.getUser(), boothId));
    }

    @PutMapping("/{id}/products")
    @Operation(summary = "Cập nhật danh sách sản phẩm hiển thị cho Designer", description = "Cập nhật danh sách sản phẩm của Exhibitor hiển thị cho Designer trong quá trình thực hiện thiết kế.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> updateProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDesignRequestProductsRequest request) {
        return ok(designRequestService.updatePendingProducts(userDetails.getUser(), id, request.getProductIds()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Exhibitor hủy yêu cầu thiết kế booth", description = "Hủy yêu cầu thiết kế booth khi chưa được phân công cho Designer (ở trạng thái PENDING).")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> cancelRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designRequestService.cancelRequest(userDetails.getUser(), id));
    }

    @PostMapping("/{id}/cancellation-request")
    @Operation(summary = "Exhibitor gửi yêu cầu hủy thiết kế sau khi đã phân công", description = "Gửi yêu cầu hủy thiết kế kèm theo lý do sau khi Designer đã được phân công. Yêu cầu này cần được Admin duyệt.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> requestCancellation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody RequestDesignCancellationRequest request) {
        return ok(designRequestService.requestCancellation(userDetails.getUser(), id, request.getReason()));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Exhibitor xem tin nhắn trao đổi làm rõ", description = "Lấy lịch sử tin nhắn trao đổi làm rõ giữa Exhibitor và Designer cho yêu cầu thiết kế này.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestMessageResponseDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt") Pageable pageable) {
        return ok(communicationService.getForExhibitor(userDetails.getUser(), id, pageable));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Exhibitor gửi tin nhắn trao đổi làm rõ", description = "Gửi tin nhắn trao đổi làm rõ cho Designer về yêu cầu thiết kế này.")
    public ResponseEntity<ApiResponse<DesignRequestMessageResponseDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody CreateDesignRequestMessageRequest request) {
        return created(communicationService.sendForExhibitor(userDetails.getUser(), id, request.getMessage()));
    }

    @PostMapping("/{id}/approve")
    /*
     * @Operation(summary = "Exhibitor duyệt bản thảo và áp dụng vào booth",
     * description =
     * "Duyệt bản thảo thiết kế mới nhất của Designer và tự động áp dụng bản thiết kế 3D này vào booth chính thức. Có thể truyền danh sách acceptedMediaAssetIds để lựa chọn tệp media asset đính kèm."
     * )
     */
    @Operation(summary = "Approve the latest design draft", description = "Applies the latest submitted draft to the booth. Only DesignDraftMediaAsset IDs listed in acceptedMediaAssetIds become company media; an omitted, null, or empty list accepts no media.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> approveDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveDesignDraftRequest request) {
        return ok(designRequestService.approveDraft(userDetails.getUser(), id, request));
    }

    @GetMapping("/{id}/review-workspace")
    @Operation(summary = "Mở workspace review bản thảo thiết kế mới nhất", description = "Lấy thông tin workspace review bản thảo thiết kế nháp (draft) được nộp gần nhất từ Designer.")
    public ResponseEntity<ApiResponse<ExhibitorDesignReviewWorkspaceResponseDTO>> getReviewWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(workspaceService.getReviewWorkspace(userDetails.getUser(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Exhibitor yêu cầu designer sửa bản thảo", description = "Từ chối bản thiết kế nháp hiện tại và gửi yêu cầu phản hồi để Designer chỉnh sửa lại bản thảo.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> rejectDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody RejectDesignDraftRequest request) {
        return ok(designRequestService.rejectDraft(userDetails.getUser(), id, request));
    }
}
