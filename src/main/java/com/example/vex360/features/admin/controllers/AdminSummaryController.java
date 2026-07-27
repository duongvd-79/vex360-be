package com.example.vex360.features.admin.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.admin.dtos.response.AdminSummaryResponseDTO;
import com.example.vex360.features.admin.services.AdminSummaryService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Summary", description = "Thống kê các yêu cầu đang chờ Admin xử lý")
public class AdminSummaryController extends BaseController {

    private final AdminSummaryService adminSummaryService;

    @GetMapping("/summary")
    @Operation(summary = "Admin xem tổng số yêu cầu đang chờ xử lý")
    public ResponseEntity<ApiResponse<AdminSummaryResponseDTO>> getSummary() {
        return ok(adminSummaryService.getSummary());
    }
}
