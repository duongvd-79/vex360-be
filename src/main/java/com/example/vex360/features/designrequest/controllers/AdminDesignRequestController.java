package com.example.vex360.features.designrequest.controllers;

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

import com.example.vex360.features.designrequest.dtos.request.AssignDesignRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignAssignmentAnalyticsResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
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
    @Operation(summary = "Admin xem danh sách yêu cầu thiết kế booth")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @RequestParam(required = false) DesignRequestStatus status,
            @RequestParam(required = false) UUID designerId,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForAdmin(status, designerId, pageable));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Admin phân công designer")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> assignRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDesignRequest request) {
        return ok(designRequestService.assignRequest(id, request));
    }

    @GetMapping("/assignment-analytics")
    @Operation(summary = "Admin xem analytics phân công designer")
    public ResponseEntity<ApiResponse<DesignAssignmentAnalyticsResponseDTO>> getAssignmentAnalytics() {
        return ok(designRequestService.getAssignmentAnalytics());
    }
}
