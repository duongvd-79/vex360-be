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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestDetailDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer/exhibitions/{exhibitionUuid}/booths")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
public class OrganizerBoothController extends BaseController {
    private final BoothReviewService boothReviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getBooths(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoothStatus status,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10) Pageable pageable) {
        return ok(boothReviewService.getBoothsForOrganizer(
                userDetails.getUser(),
                exhibitionUuid,
                keyword,
                status,
                pageable));
    }

    @GetMapping("/{boothId}/latest-review-request")
    public ResponseEntity<ApiResponse<BoothReviewRequestDetailDTO>> getLatestReviewRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId) {
        return ok(boothReviewService.getLatestReviewRequestForOrganizer(
                userDetails.getUser(),
                exhibitionUuid,
                boothId));
    }

    @GetMapping("/{boothId}/review-requests")
    public ResponseEntity<ApiResponse<PageResponse<BoothReviewRequestSummaryDTO>>> getReviewRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(boothReviewService.getReviewHistoryForOrganizer(
                userDetails.getUser(),
                exhibitionUuid,
                boothId,
                pageable));
    }
}
