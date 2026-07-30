package com.example.vex360.features.analytics.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/exhibitions")
@RequiredArgsConstructor
@Tag(name = "Public Exhibition Analytics", description = "Endpoint xem thông tin chi tiết triển lãm công khai kèm lượt truy cập")
public class PublicAnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem thông tin triển lãm công khai theo UUID", description = "Lấy thông tin chi tiết triển lãm và visitor count dựa trên UUID.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getExhibitionByUuid(
            @Parameter(description = "UUID của triển lãm") @PathVariable("uuid") UUID uuid) {
        ExhibitionResponseDTO response = analyticsService.getPublicExhibitionDetail(uuid);
        return ok(response);
    }
}
