package com.example.vex360.features.booth.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
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
@RequestMapping("/api/v1/visitor/exhibitions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('VISITOR', 'EXHIBITOR', 'ORGANIZER', 'ADMIN')")
@Tag(name = "Visitor Exhibition Participation", description = "Các endpoint dành cho người dùng (Visitor/Exhibitor/Organizer/Admin) tham gia sự kiện triển lãm đang diễn ra")
public class VisitorBoothController extends BaseController {

    private final VisitorBoothService visitorBoothService;

    @GetMapping("/{exhibitionUuid}/booths")
    @Operation(summary = "Lấy danh sách các gian hàng trong sự kiện triển lãm đang diễn ra", description = "Trả về danh sách các gian hàng đã duyệt (PUBLISHED) thuộc sự kiện triển lãm có trạng thái ACTIVE.")
    public ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> getPublishedBooths(
            @PathVariable UUID exhibitionUuid,
            @Parameter(description = "Từ khóa tìm kiếm theo tên gian hàng") @RequestParam(required = false) String keyword,
            @Parameter(description = "Mức độ ưu tiên hiển thị gian hàng") @RequestParam(required = false) BoothListingPriority listingPriority,
            @ParameterObject @PageableDefault(page = 0, size = 10) Pageable pageable) {
        PageResponse<BoothResponseDTO> response = visitorBoothService.getPublishedBooths(
                exhibitionUuid, keyword, listingPriority, pageable);
        return ok(response);
    }

    @GetMapping("/{exhibitionUuid}/products")
    @Operation(summary = "Tìm sản phẩm đang được trưng bày trong triển lãm", description = "Trả về sản phẩm đang hoạt động cùng danh sách booth, panorama và hotspot đang trưng bày sản phẩm đó.")
    public ResponseEntity<ApiResponse<PageResponse<VisitorProductSearchResponseDTO>>> searchDisplayedProducts(
            @PathVariable UUID exhibitionUuid,
            @Parameter(description = "Từ khóa tìm kiếm theo tên sản phẩm, SKU hoặc tên gian hàng") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<VisitorProductSearchResponseDTO> response = visitorBoothService.searchDisplayedProducts(
                exhibitionUuid, keyword, pageable);
        return ok(response);
    }

    @GetMapping("/{exhibitionUuid}/products/{productId}")
    @Operation(summary = "Lấy chi tiết sản phẩm đang được trưng bày trong triển lãm")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getDisplayedProductDetail(
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID productId) {
        ProductResponseDTO response = visitorBoothService.getDisplayedProductDetail(exhibitionUuid, productId);
        return ok(response);
    }

    @GetMapping("/{exhibitionUuid}/booths/{boothId}")
    @Operation(summary = "Lấy chi tiết không gian gian hàng 360 độ", description = "Trả về cấu trúc không gian gian hàng bao gồm danh sách Panoramas, Hotspots, sản phẩm và media liên kết để dựng không gian tham quan 360 độ.")
    public ResponseEntity<ApiResponse<BoothResponseDTO>> getBoothTourDetail(
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId) {
        BoothResponseDTO response = visitorBoothService.getBoothTourDetail(exhibitionUuid, boothId);
        return ok(response);
    }
}
