package com.example.vex360.features.booth.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.product.dtos.response.VisitorProductSearchResponseDTO;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/exhibitions")
@RequiredArgsConstructor
@Tag(name = "Public Booths", description = "Các endpoint xem gian hàng công khai (không cần đăng nhập)")
public class PublicBoothController extends BaseController {

    private final VisitorBoothService visitorBoothService;

    @GetMapping("/{exhibitionUuid}/booths")
    @Operation(summary = "Lấy danh sách các gian hàng công khai trong sự kiện triển lãm", description = "Trả về danh sách các gian hàng đã duyệt (PUBLISHED) thuộc sự kiện triển lãm, có hỗ trợ lọc gian hàng nổi bật FEATURED.")
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getPublishedBooths(
            @PathVariable UUID exhibitionUuid,
            @Parameter(description = "Từ khóa tìm kiếm theo tên gian hàng") @RequestParam(required = false) String keyword,
            @Parameter(description = "Mức độ ưu tiên hiển thị gian hàng (FEATURED / NORMAL)") @RequestParam(required = false) BoothListingPriority listingPriority,
            @ParameterObject @PageableDefault(page = 0, size = 10) Pageable pageable) {
        PageResponse<BoothResponseDTO> response = visitorBoothService.getPublishedBooths(
                exhibitionUuid, keyword, listingPriority, pageable);
        return ok(response);
    }

    @GetMapping("/{exhibitionUuid}/products")
    @Operation(summary = "Tìm sản phẩm đang được trưng bày trong triển lãm công khai")
    public ResponseEntity<ApiResponse<PageResponse<VisitorProductSearchResponseDTO>>> searchDisplayedProducts(
            @PathVariable UUID exhibitionUuid,
            @Parameter(description = "Từ khóa tìm kiếm theo tên sản phẩm, SKU hoặc tên gian hàng") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "name") Pageable pageable) {
        return ok(visitorBoothService.searchDisplayedProducts(exhibitionUuid, keyword, pageable));
    }
}
