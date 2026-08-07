package com.example.vex360.features.exhibition.controllers;

import java.time.LocalDate;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/exhibitions")
@RequiredArgsConstructor
@Tag(name = "Public Exhibitions", description = "Các endpoint xem thông tin triển lãm công khai (không cần đăng nhập)")
public class PublicExhibitionController extends BaseController {

    private final ExhibitionService exhibitionService;
    private final VisitorBoothService visitorBoothService;

    @GetMapping
    @Operation(summary = "Tìm kiếm triển lãm công khai cho Visitor", description = "Lấy danh sách các triển lãm đang ở trạng thái công bố (PUBLISHED/ACTIVE/COMPLETED), có phân trang, lọc và tìm kiếm. Ẩn toàn bộ ID khóa chính của triển lãm.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionResponseDTO>>> searchExhibitions(
            @Parameter(description = "Từ khóa tìm kiếm theo tên triển lãm hoặc nhà tổ chức") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo trạng thái công khai: PUBLISHED, ACTIVE hoặc COMPLETED") @RequestParam(required = false) ExhibitionStatus status,
            @Parameter(description = "Lọc theo lĩnh vực") @RequestParam(required = false) String category,
            @Parameter(description = "Từ ngày bắt đầu") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Đến ngày kết thúc") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> response = exhibitionService.searchExhibitionsForVisitor(
                keyword, status, category, startDate, endDate, pageable);
        return ok(response);
    }

    @GetMapping("/{exhibitionUuid}/booths")
    @Operation(summary = "Lấy danh sách các gian hàng công khai trong sự kiện triển lãm", description = "Trả về danh sách các gian hàng đã duyệt (PUBLISHED) thuộc sự kiện triển lãm (bao gồm hỗ trợ lọc gian hàng nổi bật FEATURED) dành cho mọi đối tượng truy cập.")
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getPublishedBooths(
            @PathVariable UUID exhibitionUuid,
            @Parameter(description = "Từ khóa tìm kiếm theo tên gian hàng") @RequestParam(required = false) String keyword,
            @Parameter(description = "Mức độ ưu tiên hiển thị gian hàng (FEATURED / NORMAL)") @RequestParam(required = false) BoothListingPriority listingPriority,
            @ParameterObject @PageableDefault(page = 0, size = 10) Pageable pageable) {
        PageResponse<BoothResponseDTO> response = visitorBoothService.getPublishedBooths(
                exhibitionUuid, keyword, listingPriority, pageable);
        return ok(response);
    }
}

