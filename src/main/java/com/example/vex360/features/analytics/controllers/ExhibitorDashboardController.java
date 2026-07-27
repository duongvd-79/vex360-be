package com.example.vex360.features.analytics.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.analytics.dtos.response.ExhibitorDashboardOverviewDTO;
import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@Tag(name = "Exhibitor Dashboard", description = "Thống kê tổng hợp toàn bộ gian hàng cho bảng điều khiển exhibitor")
public class ExhibitorDashboardController extends BaseController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Thống kê tổng hợp cho bảng điều khiển exhibitor (gộp mọi gian hàng)")
    public ResponseEntity<ApiResponse<ExhibitorDashboardOverviewDTO>> getOverview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ok(analyticsService.getExhibitorDashboardOverview(userDetails.getUser(), startDate, endDate));
    }
}
