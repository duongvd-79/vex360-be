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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.RejectDesignDraftRequest;
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

    @PostMapping
    @Operation(summary = "Exhibitor tạo yêu cầu thiết kế booth")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> createRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateDesignRequest request) {
        return created(designRequestService.createRequest(userDetails.getUser(), request));
    }

    @GetMapping
    @Operation(summary = "Exhibitor xem danh sách yêu cầu thiết kế booth")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForExhibitor(userDetails.getUser(), status, pageable));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Exhibitor hủy yêu cầu thiết kế booth")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> cancelRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designRequestService.cancelRequest(userDetails.getUser(), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Exhibitor duyệt bản thảo và áp dụng vào booth")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> approveDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ok(designRequestService.approveDraft(userDetails.getUser(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Exhibitor yêu cầu designer sửa bản thảo")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> rejectDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody RejectDesignDraftRequest request) {
        return ok(designRequestService.rejectDraft(userDetails.getUser(), id, request));
    }
}
