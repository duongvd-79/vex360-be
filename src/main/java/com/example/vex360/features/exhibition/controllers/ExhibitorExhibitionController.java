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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/exhibitions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@Tag(name = "Exhibitor Exhibitions", description = "Xem và tìm kiếm thông tin triển lãm dành cho Đơn vị triển lãm (Exhibitor)")
public class ExhibitorExhibitionController extends BaseController {

    private final ExhibitionService exhibitionService;

    @GetMapping
    @Operation(summary = "Tìm kiếm triển lãm khả dụng cho Exhibitor", description = "Lấy danh sách các triển lãm đang ở trạng thái đăng ký (REGISTRATION) hoặc đã công bố (PUBLISHED/ACTIVE), có phân trang, lọc và tìm kiếm.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionResponseDTO>>> searchExhibitions(
            @Parameter(description = "Từ khóa tìm kiếm theo tên triển lãm hoặc nhà tổ chức") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo lĩnh vực") @RequestParam(required = false) String category,
            @Parameter(description = "Từ ngày bắt đầu") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Đến ngày kết thúc") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> response = exhibitionService.searchExhibitionsForExhibitor(
                keyword, category, startDate, endDate, pageable);
        return ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem chi tiết triển lãm theo UUID dành cho Exhibitor", description = "Lấy chi tiết triển lãm và danh sách gói dịch vụ tương ứng dựa trên UUID nếu triển lãm đang ở trạng thái khả dụng cho Exhibitor.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getExhibitionDetails(@PathVariable("uuid") UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.getExhibitionDetailForExhibitor(uuid);
        return ok(response);
    }
}
