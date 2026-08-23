package com.example.vex360.features.hall.controllers;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.hall.dtos.request.RejectHallReviewRequest;
import com.example.vex360.features.hall.dtos.response.HallReviewRequestDetailDTO;
import com.example.vex360.features.hall.dtos.response.HallReviewRequestSummaryDTO;
import com.example.vex360.features.hall.enums.HallReviewStatus;
import com.example.vex360.features.hall.services.HallReviewService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/hall-review-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin Hall Review", description = "Admin xét duyệt nội dung sảnh")
public class AdminHallReviewController extends BaseController {
    private final HallReviewService reviewService;

    @GetMapping
    @Operation(summary = "Xem danh sách yêu cầu duyệt sảnh")
    public ResponseEntity<ApiResponse<PageResponse<HallReviewRequestSummaryDTO>>> getRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) HallReviewStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(
                    page = 0, size = 10, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(reviewService.getAdminRequests(userDetails.getUser(), status, keyword, pageable));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Xem snapshot chi tiết của yêu cầu duyệt sảnh")
    public ResponseEntity<ApiResponse<HallReviewRequestDetailDTO>> getRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID requestId) {
        return ok(reviewService.getAdminRequest(userDetails.getUser(), requestId));
    }

    @PutMapping("/{requestId}/approve")
    @Operation(summary = "Duyệt và xuất bản sảnh")
    public ResponseEntity<ApiResponse<HallReviewRequestSummaryDTO>> approve(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID requestId) {
        return ok(reviewService.approve(userDetails.getUser(), requestId));
    }

    @PutMapping("/{requestId}/reject")
    @Operation(summary = "Từ chối yêu cầu duyệt sảnh")
    public ResponseEntity<ApiResponse<HallReviewRequestSummaryDTO>> reject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectHallReviewRequest request) {
        return ok(reviewService.reject(userDetails.getUser(), requestId, request));
    }
}
