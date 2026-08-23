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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.hall.dtos.response.HallReviewRequestSummaryDTO;
import com.example.vex360.features.hall.services.HallReviewService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer/exhibitions/{exhibitionUuid}/hall/review-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
@RequireActiveCompany(roles = Role.ORGANIZER)
@Tag(name = "Organizer Hall Review", description = "Gửi và theo dõi yêu cầu duyệt sảnh")
public class OrganizerHallReviewController extends BaseController {
    private final HallReviewService reviewService;

    @PostMapping
    @Operation(summary = "Gửi sảnh để Admin xét duyệt")
    public ResponseEntity<ApiResponse<HallReviewRequestSummaryDTO>> submit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid) {
        return created(reviewService.submit(userDetails.getUser(), exhibitionUuid));
    }

    @GetMapping
    @Operation(summary = "Xem lịch sử xét duyệt sảnh")
    public ResponseEntity<ApiResponse<PageResponse<HallReviewRequestSummaryDTO>>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @ParameterObject @PageableDefault(
                    page = 0, size = 10, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(reviewService.getOrganizerHistory(userDetails.getUser(), exhibitionUuid, pageable));
    }
}
