package com.example.vex360.features.exhibition.controllers;

import java.time.LocalDate;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionSummaryResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/exhibitions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Exhibition Requests", description = "Admin xem danh sách, chi tiết và thống kê đơn đăng ký mở triển lãm")
public class AdminExhibitionController extends BaseController {

    private final ExhibitionService exhibitionService;

    @GetMapping
    @Operation(
            summary = "Admin xem danh sách yêu cầu mở triển lãm",
            description = "Trả về danh sách đơn đăng ký mở triển lãm của các organizer có phân trang. Có thể tìm kiếm theo từ khóa (tên triển lãm, tên/email organizer), lọc theo status, category, và thời gian diễn ra (startDate, endDate). Mặc định sắp xếp theo ngày tạo giảm dần.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionResponseDTO>>> getExhibitions(
            @Parameter(description = "Từ khóa tìm kiếm (tên triển lãm, tên organizer, email organizer)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Trạng thái của triển lãm")
            @RequestParam(required = false) ExhibitionStatus status,
            @Parameter(description = "Lĩnh vực/danh mục của triển lãm")
            @RequestParam(required = false) String category,
            @Parameter(description = "Ngày bắt đầu triển lãm (từ ngày)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Ngày kết thúc triển lãm (đến ngày)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> response = exhibitionService
                .searchExhibitionsForAdmin(keyword, status, category, startDate, endDate, pageable);
        return ok(response);
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Admin xem thống kê đơn đăng ký mở triển lãm",
            description = "Trả về tổng số đơn đăng ký mở triển lãm và số lượng của mỗi trạng thái (PENDING, APPROVED, REJECTED, ACTIVE, ...).")
    public ResponseEntity<ApiResponse<ExhibitionSummaryResponseDTO>> getExhibitionSummary() {
        ExhibitionSummaryResponseDTO summary = exhibitionService.getExhibitionSummary();
        return ok(summary);
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Admin xem chi tiết đơn đăng ký mở triển lãm",
            description = "Trả về thông tin chi tiết đầy đủ của đơn đăng ký mở triển lãm theo UUID, bao gồm cả các gói dịch vụ cấu hình và các ID khóa chính hệ thống.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getExhibitionByUuid(
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.getExhibitionDetailForAdmin(uuid);
        return ok(response);
    }

    @PostMapping("/{uuid}/approve")
    @Operation(
            summary = "Admin phê duyệt đơn đăng ký mở triển lãm",
            description = "Duyệt đơn đăng ký mở triển lãm đang ở trạng thái PENDING. Trạng thái của đơn sẽ được cập nhật thành APPROVED.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> approveExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.approveExhibition(userDetails.getUser(), uuid);
        return ok(response);
    }

    @PostMapping("/{uuid}/reject")
    @Operation(
            summary = "Admin từ chối đơn đăng ký mở triển lãm",
            description = "Từ chối đơn đăng ký mở triển lãm đang ở trạng thái PENDING kèm lý do từ chối. Trạng thái của đơn sẽ được cập nhật thành REJECTED.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> rejectExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid,
            @Valid @RequestBody RejectExhibitionRequest request) {
        ExhibitionResponseDTO response = exhibitionService.rejectExhibition(userDetails.getUser(), uuid, request);
        return ok(response);
    }
}
