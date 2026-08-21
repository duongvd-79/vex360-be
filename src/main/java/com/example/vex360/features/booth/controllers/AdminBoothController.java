package com.example.vex360.features.booth.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.dtos.request.BanBoothRequest;
import com.example.vex360.features.booth.dtos.request.WarnBoothRequest;
import com.example.vex360.features.booth.dtos.response.AdminBoothContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothModerationSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.AdminBoothModerationService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Booth Moderation", description = "Admin kiểm duyệt gian hàng: xem danh sách, chi tiết 360, cảnh báo (warn) và khóa (ban)")
public class AdminBoothController extends BaseController {
    private final AdminBoothModerationService moderationService;

    @GetMapping("/exhibitions/{exhibitionUuid}/booths")
    @Operation(summary = "Admin xem danh sách gian hàng trong triển lãm")
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getBooths(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoothStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ok(moderationService.getBoothsForAdmin(
                userDetails.getUser(),
                exhibitionUuid,
                keyword,
                status,
                pageable));
    }

    @GetMapping("/booths/moderation")
    @Operation(summary = "Admin xem tổng hợp các lệnh cảnh báo và ban theo gian hàng")
    public ResponseEntity<ApiResponse<PageResponse<BoothModerationSummaryDTO>>> getModerationSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ok(moderationService.getModerationSummary(
                userDetails.getUser(), keyword, pageable));
    }

    @GetMapping("/booths/{boothId}/content-overview")
    @Operation(summary = "Admin xem chi tiết thông tin và nội dung 360 gian hàng kèm trạng thái kiểm duyệt (Warn/Ban)")
    public ResponseEntity<ApiResponse<AdminBoothContentOverviewDTO>> getContentOverview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID boothId) {
        return ok(moderationService.getContentOverviewForAdmin(
                userDetails.getUser(),
                boothId));
    }

    @PostMapping("/exhibitions/{exhibitionUuid}/booths/{boothId}/warn")
    @Operation(summary = "Admin cảnh báo gian hàng (tối đa 1 lần, chỉ khả dụng trước mốc T-3)")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> warnBooth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId,
            @Valid @RequestBody WarnBoothRequest request) {
        return ok(moderationService.warnBooth(
                userDetails.getUser(),
                exhibitionUuid,
                boothId,
                request));
    }

    @PostMapping("/exhibitions/{exhibitionUuid}/booths/{boothId}/ban")
    @Operation(summary = "Admin khóa (BAN) gian hàng")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> banBooth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId,
            @Valid @RequestBody BanBoothRequest request) {
        return ok(moderationService.banBooth(
                userDetails.getUser(),
                exhibitionUuid,
                boothId,
                request));
    }
}
