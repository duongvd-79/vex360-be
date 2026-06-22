package com.example.vex360.features.partnership.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/partnership-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Partnership Requests", description = "Admin xem, duyệt và từ chối yêu cầu hợp tác")
public class AdminPartnershipRequestController {
    private final PartnershipRequestService partnershipRequestService;

    @GetMapping
    @Operation(
            summary = "Admin xem danh sách yêu cầu hợp tác",
            description = "Trả về danh sách partnership request có phân trang. Có thể lọc theo status và requestedRole; mặc định sort theo createdAt giảm dần.")
    public ResponseEntity<ApiResponse<PageResponse<PartnershipRequestResponseDTO>>> getRequests(
            @Parameter(description = "Trạng thái request cần lọc", example = "PENDING")
            @RequestParam(required = false) PartnershipRequestStatus status,
            @Parameter(description = "Role được yêu cầu; chỉ dùng EXHIBITOR hoặc ORGANIZER", example = "EXHIBITOR")
            @RequestParam(required = false) Role requestedRole,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<PartnershipRequestResponseDTO> requests = partnershipRequestService
                .getRequests(status, requestedRole, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Admin xem chi tiết yêu cầu hợp tác",
            description = "Trả về toàn bộ thông tin của một partnership request theo ID.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> getRequestById(
            @Parameter(description = "ID partnership request", example = "84257b4d-e9f3-4a41-a9d9-b0e8d1b4d9e3")
            @PathVariable UUID id) {
        PartnershipRequestResponseDTO request = partnershipRequestService.getRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Admin duyệt yêu cầu hợp tác",
            description = "Chỉ duyệt request PENDING. Guest request sẽ tạo user + company; authenticated request sẽ cập nhật role và tạo company nếu user chưa có.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> approveRequest(
            @Parameter(description = "ID partnership request cần duyệt", example = "84257b4d-e9f3-4a41-a9d9-b0e8d1b4d9e3")
            @PathVariable UUID id) {
        PartnershipRequestResponseDTO request = partnershipRequestService.approveRequest(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PostMapping("/{id}/reject")
    @Operation(
            summary = "Admin từ chối yêu cầu hợp tác",
            description = "Chỉ từ chối request PENDING. API lưu reviewNote và thời điểm reviewedAt; không gửi email reject.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> rejectRequest(
            @Parameter(description = "ID partnership request cần từ chối", example = "84257b4d-e9f3-4a41-a9d9-b0e8d1b4d9e3")
            @PathVariable UUID id,
            @Valid @RequestBody RejectPartnershipRequest rejectRequest) {
        PartnershipRequestResponseDTO request = partnershipRequestService.rejectRequest(id, rejectRequest);
        return ResponseEntity.ok(ApiResponse.success(request));
    }
}
