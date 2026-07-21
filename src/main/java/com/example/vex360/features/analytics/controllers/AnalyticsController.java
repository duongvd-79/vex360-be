package com.example.vex360.features.analytics.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.analytics.dtos.request.RecordAnalyticsEventRequest;
import com.example.vex360.features.analytics.services.AnalyticsAsyncRecorder;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Ghi nhận sự kiện tương tác của người dùng")
public class AnalyticsController extends BaseController {

    private final AnalyticsAsyncRecorder analyticsAsyncRecorder;

    @PostMapping("/events")
    @Operation(summary = "Ghi nhận một sự kiện analytics (xử lý bất đồng bộ)")
    public ResponseEntity<ApiResponse<Void>> recordEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RecordAnalyticsEventRequest request) {
        // Bắn sang luồng nền -> trả về ngay, không bắt khách chờ INSERT
        analyticsAsyncRecorder.recordEventAsync(userDetails.getUser(), request);
        return ok(null);
    }
}
