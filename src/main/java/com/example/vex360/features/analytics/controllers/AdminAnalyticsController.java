package com.example.vex360.features.analytics.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.analytics.dtos.response.AdminSystemAnalyticsDTO;
import com.example.vex360.features.analytics.services.AdminAnalyticsService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin Analytics", description = "Thống kê tổng quan toàn hệ thống")
public class AdminAnalyticsController extends BaseController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Thống kê tổng quan toàn hệ thống theo khoảng ngày")
    public ResponseEntity<ApiResponse<AdminSystemAnalyticsDTO>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ok(adminAnalyticsService.getSummary(startDate, endDate));
    }
}
