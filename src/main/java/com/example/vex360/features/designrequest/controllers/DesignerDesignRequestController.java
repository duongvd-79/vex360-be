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
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
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
@RequestMapping("/api/v1/designer/design-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DESIGNER')")
@Tag(name = "Designer - Design Requests", description = "Designer nhận việc và gửi bản thảo booth")
public class DesignerDesignRequestController extends BaseController {
    private final DesignRequestService designRequestService;

    @GetMapping
    @Operation(summary = "Designer xem danh sách yêu cầu được phân công")
    public ResponseEntity<ApiResponse<PageResponse<DesignRequestResponseDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) DesignRequestStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(designRequestService.getRequestsForDesigner(userDetails.getUser(), status, pageable));
    }

    @PostMapping("/{id}/drafts")
    @Operation(summary = "Designer gửi bản thảo booth hoàn chỉnh")
    public ResponseEntity<ApiResponse<DesignRequestResponseDTO>> submitDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitDesignDraftRequest request) {
        return ok(designRequestService.submitDraft(userDetails.getUser(), id, request));
    }
}
