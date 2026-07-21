package com.example.vex360.features.analytics.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsDetailDTO;
import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsOverviewDTO;
import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer/exhibitions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
@Tag(name = "Organizer Analytics", description = "Thống kê triển lãm cho organizer")
public class OrganizerAnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;

    @GetMapping("/analytics")
    @Operation(summary = "Danh sách triển lãm của organizer để xem thống kê")
    public ResponseEntity<ApiResponse<List<ExhibitionAnalyticsOverviewDTO>>> getOverview(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ok(analyticsService.getOrganizerOverview(userDetails.getUser()));
    }

    @GetMapping("/{uuid}/analytics")
    @Operation(summary = "Thống kê chi tiết một triển lãm")
    public ResponseEntity<ApiResponse<ExhibitionAnalyticsDetailDTO>> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID uuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ok(analyticsService.getExhibitionAnalytics(userDetails.getUser(), uuid, startDate, endDate));
    }
}
