package com.example.vex360.features.exhibition.controllers;

import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
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
    @Operation(summary = "Tạo triển lãm mới", description = "Tạo một sự kiện triển lãm mới và gán nhà tổ chức hiện tại làm người sở hữu. Yêu cầu số đơn pending hiện tại phải dưới 3. Bắt buộc tải lên ảnh bìa Key Visual. Cho phép tải lên danh sách ảnh logo nhà tài trợ (sponsorLogos).")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> createExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateExhibitionRequest request,
            @RequestPart("keyVisual") MultipartFile keyVisual,
            @RequestPart(value = "sponsorLogos", required = false) List<MultipartFile> sponsorLogos) {
        ExhibitionResponseDTO response = exhibitionService.createExhibition(userDetails.getUser(), request, keyVisual,
                sponsorLogos);
        return created(response);
    }

    @GetMapping
    @Operation(summary = "Xem danh sách triển lãm của tôi", description = "Lấy danh sách các đơn đăng ký mở triển lãm do chính nhà tổ chức hiện tại đăng ký, có phân trang, lọc và tìm kiếm.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionResponseDTO>>> getMyExhibitions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Từ khóa tìm kiếm theo tên triển lãm") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) ExhibitionStatus status,
            @Parameter(description = "Lọc theo lĩnh vực") @RequestParam(required = false) String category,
            @Parameter(description = "Từ ngày bắt đầu") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Đến ngày kết thúc") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> response = exhibitionService
                .searchExhibitionsForOrganizer(userDetails.getUser(), keyword, status, category, startDate, endDate,
                        pageable);
        return ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem chi tiết triển lãm của tôi theo UUID", description = "Lấy thông tin chi tiết đầy đủ của một triển lãm thuộc sở hữu của nhà tổ chức, bao gồm các cấu hình gói dịch vụ.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getMyExhibitionDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.getExhibitionDetailForOrganizer(userDetails.getUser(), uuid);
        return ok(response);
    }

    @PutMapping(path = "/{uuid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật đơn đăng ký triển lãm", description = "Cập nhật thông tin chi tiết của đơn đăng ký triển lãm (chỉ được phép khi đơn đang ở trạng thái PENDING). Không cho phép thay đổi trạng thái hoặc hủy đơn tại đây. Cho phép cập nhật lại Key Visual.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> updateExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Valid @RequestPart("metadata") CreateExhibitionRequest request,
            @RequestPart(value = "keyVisual", required = false) MultipartFile keyVisual) {
        ExhibitionResponseDTO response = exhibitionService.updateExhibitionForOrganizer(userDetails.getUser(), uuid,
                request, keyVisual);
        return ok(response);
    }

    @PutMapping(path = "/{uuid}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật các tệp phương tiện giới thiệu sự kiện", description = "Cập nhật Video giới thiệu (Trailer Video), Sơ đồ mặt bằng (Floor Plan), Hướng dẫn tham quan (Guideline). Chỉ được phép thực hiện sau khi triển lãm đã được duyệt.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> updateExhibitionMedia(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @RequestPart(value = "trailerVideo", required = false) MultipartFile trailerVideo,
            @RequestPart(value = "floorPlan", required = false) MultipartFile floorPlan,
            @RequestPart(value = "guideline", required = false) MultipartFile guideline) {
        ExhibitionResponseDTO response = exhibitionService.updateExhibitionMedia(userDetails.getUser(), uuid,
                trailerVideo, floorPlan, guideline);
        return ok(response);
    }

    // Sponsor Media CRU
    @PostMapping(path = "/{uuid}/sponsors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Thêm logo nhà tài trợ", description = "Upload thêm một logo nhà tài trợ mới cho triển lãm.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> uploadSponsorLogo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @RequestPart("file") MultipartFile file) {
        ExhibitionResponseDTO response = exhibitionService.uploadSponsorLogo(userDetails.getUser(), uuid, file);
        return ok(response);
    }

    @PutMapping(path = "/{uuid}/sponsors/{assetId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cập nhật logo nhà tài trợ", description = "Cập nhật/thay đổi ảnh logo nhà tài trợ hiện có.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> updateSponsorLogo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Parameter(description = "ID của ảnh logo tài trợ") @PathVariable UUID assetId,
            @RequestPart("file") MultipartFile file) {
        ExhibitionResponseDTO response = exhibitionService.updateSponsorLogo(userDetails.getUser(), uuid, assetId,
                file);
        return ok(response);
    }

    @DeleteMapping(path = "/{uuid}/sponsors/{assetId}")
    @Operation(summary = "Xoá logo nhà tài trợ", description = "Xoá hoàn toàn logo nhà tài trợ khỏi triển lãm.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> deleteSponsorLogo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Parameter(description = "ID của ảnh logo tài trợ") @PathVariable UUID assetId) {
        ExhibitionResponseDTO response = exhibitionService.deleteSponsorLogo(userDetails.getUser(), uuid, assetId);
        return ok(response);
    }

    // Exhibition Package CRU
    @PostMapping(path = "/{uuid}/packages")
    @Operation(summary = "Thêm gói dịch vụ mới", description = "Thêm cấu hình gói dịch vụ mới cho triển lãm.")
    public ResponseEntity<ApiResponse<ExhibitionPackageResponseDTO>> addExhibitionPackage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Valid @RequestBody ConfigureExhibitionPackageRequest request) {
        ExhibitionPackageResponseDTO response = exhibitionService.addExhibitionPackage(userDetails.getUser(), uuid,
                request);
        return created(response);
    }

    @PutMapping(path = "/{uuid}/packages/{packageId}")
    @Operation(summary = "Cập nhật gói dịch vụ", description = "Cập nhật lại giá bán hoặc loại gói dịch vụ của triển lãm (chỉ khi chưa có doanh nghiệp đăng ký).")
    public ResponseEntity<ApiResponse<ExhibitionPackageResponseDTO>> updateExhibitionPackage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Parameter(description = "ID gói triển lãm") @PathVariable Integer packageId,
            @Valid @RequestBody ConfigureExhibitionPackageRequest request) {
        ExhibitionPackageResponseDTO response = exhibitionService.updateExhibitionPackage(userDetails.getUser(), uuid,
                packageId, request);
        return ok(response);
    }

    @DeleteMapping(path = "/{uuid}/packages/{packageId}")
    @Operation(summary = "Xoá gói dịch vụ", description = "Xoá hoàn toàn cấu hình gói dịch vụ khỏi triển lãm (chỉ khi chưa có doanh nghiệp đăng ký).")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> deleteExhibitionPackage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "UUID của triển lãm") @PathVariable UUID uuid,
            @Parameter(description = "ID gói triển lãm") @PathVariable Integer packageId) {
        ExhibitionResponseDTO response = exhibitionService.deleteExhibitionPackage(userDetails.getUser(), uuid,
                packageId);
        return ok(response);
    }
}
