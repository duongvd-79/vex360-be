package com.example.vex360.features.booth.controllers;

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
import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer/exhibitions/{exhibitionUuid}/booth-review-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
@RequireActiveCompany(roles = Role.ORGANIZER)
public class OrganizerBoothReviewController extends BaseController {
    private final BoothReviewService boothReviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BoothReviewRequestSummaryDTO>>> getReviewRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @RequestParam(required = false) BoothReviewStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(boothReviewService.getRequestsForOrganizer(
                userDetails.getUser(),
                exhibitionUuid,
                status,
                keyword,
                pageable));
    }

    @PutMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<BoothReviewRequestSummaryDTO>> approve(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID requestId) {
        return ok(boothReviewService.approve(userDetails.getUser(), exhibitionUuid, requestId));
    }

    @PutMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<BoothReviewRequestSummaryDTO>> reject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectBoothReviewRequest request) {
        return ok(boothReviewService.reject(userDetails.getUser(), exhibitionUuid, requestId, request));
    }
}
