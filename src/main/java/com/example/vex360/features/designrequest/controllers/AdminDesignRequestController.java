package com.example.vex360.features.designrequest.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.designrequest.dtos.request.AssignDesignRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignAssignmentAnalyticsResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.dtos.request.DecideDesignCancellationRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignAssignmentCandidateResponseDTO;
import java.util.List;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/design-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Design Requests", description = "Admin quản lý và phân công yêu cầu thiết kế booth")
public class AdminDesignRequestController extends BaseController {
    private final DesignRequestService designRequestService;

    @GetMapping
    @Operation(summary = "Admin xem danh sách yêu cầu thiết kế booth", description = "Lấy danh sách các yêu cầu thiết kế booth của toàn hệ thống, hỗ trợ tìm kiếm, lọc theo trạng thái và phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForAdmin(keyword, status, pageable));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Admin phân công designer", description = "Phân công một Designer chịu trách nhiệm thực hiện yêu cầu thiết kế booth.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> assignRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDesignRequest request) {
        return ok(designRequestService.assignRequest(id, request));
    }

    @PostMapping("/{id}/cancellation-decision")
    @Operation(summary = "Quyết định yêu cầu hủy thiết kế từ Exhibitor", description = "Admin phê duyệt hoặc từ chối yêu cầu hủy thiết kế booth từ phía Exhibitor.")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> decideCancellation(
            @PathVariable UUID id,
            @Valid @RequestBody DecideDesignCancellationRequest request) {
        return ok(designRequestService.decideCancellation(id, request.getApprove(), request.getNote()));
    }

    @GetMapping("/assignment-analytics")
    @Operation(summary = "Admin xem phân tích phân công designer", description = "Lấy dữ liệu thống kê, phân tích về tình hình phân công thiết kế của các Designer.")
    public ResponseEntity<ApiResponse<DesignAssignmentAnalyticsResponseDTO>> getAssignmentAnalytics(
            @RequestParam(required = false) DesignRequestMode mode) {
        return ok(designRequestService.getAssignmentAnalytics(mode));
    }

    @GetMapping("/assignment-candidates")
    @Operation(summary = "Lấy danh sách ứng viên Designer để phân công", description = "Trả về danh sách các Designer cùng với thông tin số lượng công việc hiện tại và số slot trống để phân công.")
    public ResponseEntity<ApiResponse<List<DesignAssignmentCandidateResponseDTO>>> getAssignmentCandidates() {
        return ok(designRequestService.getAssignmentCandidates());
    }

    @DeleteMapping("/{id}/assets")
    @Operation(summary = "Admin dọn dẹp asset không sử dụng", description = "Giải phóng các asset thiết kế nháp không còn được booth sử dụng để tối ưu bộ nhớ.")
    public ResponseEntity<ApiResponse<Integer>> cleanupAssets(@PathVariable UUID id) {
        return ok(designRequestService.cleanupTerminalAssets(id));
    }
}
