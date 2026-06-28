package com.example.vex360.features.exhibition.controllers;

import java.time.LocalDate;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
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
@RequestMapping("/api/v1/organizer/exhibitions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
@Tag(name = "Organizer Exhibitions", description = "Quản lý triển lãm và thiết lập gói dịch vụ dành cho Nhà tổ chức (Organizer)")
public class OrganizerExhibitionController extends BaseController {

    private final ExhibitionService exhibitionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo triển lãm mới", description = "Tạo một sự kiện triển lãm mới và gán nhà tổ chức hiện tại làm người sở hữu. Yêu cầu số đơn pending hiện tại phải dưới 3. Bắt buộc tải lên ảnh bìa Key Visual.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> createExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateExhibitionRequest request,
            @RequestPart("keyVisual") MultipartFile keyVisual) {
        ExhibitionResponseDTO response = exhibitionService.createExhibition(userDetails.getUser(), request, keyVisual);
        return created(response);
    }

    @GetMapping
    @Operation(summary = "Xem danh sách triển lãm của tôi", description = "Lấy danh sách các đơn đăng ký mở triển lãm do chính nhà tổ chức hiện tại đăng ký, có phân trang, lọc và tìm kiếm.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionResponseDTO>>> getMyExhibitions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Từ khóa tìm kiếm theo tên triển lãm")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo trạng thái")
            @RequestParam(required = false) ExhibitionStatus status,
            @Parameter(description = "Lọc theo lĩnh vực")
            @RequestParam(required = false) String category,
            @Parameter(description = "Từ ngày bắt đầu")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Đến ngày kết thúc")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> response = exhibitionService
                .searchExhibitionsForOrganizer(userDetails.getUser(), keyword, status, category, startDate, endDate, pageable);
        return ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem chi tiết triển lãm của tôi theo UUID", description = "Lấy thông tin chi tiết đầy đủ của một triển lãm thuộc sở hữu của nhà tổ chức, bao gồm các cấu hình gói dịch vụ.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getMyExhibitionDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.getExhibitionDetailForOrganizer(userDetails.getUser(), uuid);
        return ok(response);
    }

    @PutMapping(path = "/{uuid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật đơn đăng ký triển lãm", description = "Cập nhật thông tin chi tiết của đơn đăng ký triển lãm (chỉ được phép khi đơn đang ở trạng thái PENDING). Không cho phép thay đổi trạng thái hoặc hủy đơn tại đây. Cho phép cập nhật lại Key Visual.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> updateExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid,
            @Valid @RequestPart("metadata") CreateExhibitionRequest request,
            @RequestPart(value = "keyVisual", required = false) MultipartFile keyVisual) {
        ExhibitionResponseDTO response = exhibitionService.updateExhibitionForOrganizer(userDetails.getUser(), uuid, request, keyVisual);
        return ok(response);
    }

    @PutMapping(path = "/{uuid}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật các tệp phương tiện giới thiệu sự kiện", description = "Cập nhật Video giới thiệu (Trailer Video), Sơ đồ mặt bằng (Floor Plan), Hướng dẫn tham quan (Guideline). Chỉ được phép thực hiện sau khi triển lãm đã được duyệt.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> updateExhibitionMedia(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm")
            @PathVariable UUID uuid,
            @RequestPart(value = "trailerVideo", required = false) MultipartFile trailerVideo,
            @RequestPart(value = "floorPlan", required = false) MultipartFile floorPlan,
            @RequestPart(value = "guideline", required = false) MultipartFile guideline) {
        ExhibitionResponseDTO response = exhibitionService.updateExhibitionMedia(userDetails.getUser(), uuid, trailerVideo, floorPlan, guideline);
        return ok(response);
    }
}
