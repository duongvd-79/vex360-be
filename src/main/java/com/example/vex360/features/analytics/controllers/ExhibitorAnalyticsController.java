package com.example.vex360.features.analytics.controllers;

import java.time.LocalDate;
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

import com.example.vex360.features.analytics.dtos.response.BoothAnalyticsDetailDTO;
import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/booths")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@Tag(name = "Exhibitor Analytics", description = "Thống kê gian hàng cho exhibitor")
public class ExhibitorAnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{boothId}/analytics")
    @Operation(summary = "Thống kê chi tiết một gian hàng của exhibitor")
    public ResponseEntity<ApiResponse<BoothAnalyticsDetailDTO>> getBoothAnalytics(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ok(analyticsService.getBoothAnalytics(userDetails.getUser(), boothId, startDate, endDate));
    }
}
