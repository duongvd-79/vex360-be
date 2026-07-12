package com.example.vex360.features.exhibition.controllers;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.dtos.request.ExhibitorRegistrationRequestDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/registrations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@Tag(name = "Exhibitor Registrations", description = "Đăng ký triển lãm và thanh toán dành cho Đơn vị triển lãm (Exhibitor)")
public class ExhibitorRegistrationController extends BaseController {

    private final ExhibitorRegistrationService exhibitorRegistrationService;

    @PostMapping
    @Operation(summary = "Đăng ký gói triển lãm", description = "Đăng ký một gói triển lãm cụ thể. Nếu gói trả phí, hệ thống trả về URL thanh toán PayOS. Nếu gói miễn phí (0 VND), hệ thống tự động duyệt đăng ký.")
    public ResponseEntity<ApiResponse<ExhibitorRegistrationResponseDTO>> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ExhibitorRegistrationRequestDTO request) {
        ExhibitorRegistration registration = exhibitorRegistrationService.initializeRegistration(
                userDetails.getUser().getId(),
                request.getExhibitionPackageId(),
                request.getParticipationReason());
        ExhibitorRegistrationResponseDTO response = exhibitorRegistrationService.getRegistrationDetails(
                registration.getUuid(),
                userDetails.getUser().getId());
        return created(response);
    }

    @GetMapping
    @Operation(summary = "Xem danh sách các đơn đăng ký triển lãm của tôi", description = "Lấy danh sách các triển lãm đã đăng ký tham gia, có phân trang, lọc và tìm kiếm.")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitorRegistrationResponseDTO>>> getMyRegistrations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) ExhibitorRegistrationStatus status,
            @Parameter(description = "Từ khoá tìm kiếm theo tên triển lãm") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ExhibitorRegistrationResponseDTO> response = exhibitorRegistrationService
                .getRegistrationsForExhibitor(userDetails.getUser(), status, keyword, pageable);
        return ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem chi tiết đăng ký triển lãm theo UUID", description = "Lấy thông tin chi tiết và trạng thái thanh toán của đơn đăng ký.")
    public ResponseEntity<ApiResponse<ExhibitorRegistrationResponseDTO>> getRegistrationDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID registrationUuid) {
        ExhibitorRegistrationResponseDTO response = exhibitorRegistrationService.getRegistrationDetails(
                registrationUuid,
                userDetails.getUser().getId());
        return ok(response);
    }

    @GetMapping("/{uuid}/payment-status")
    @Operation(summary = "Kiểm tra trạng thái thanh toán", description = "Kiểm tra trạng thái thanh toán và thông tin đăng ký dựa trên UUID lượt đăng ký của đơn vị triển lãm.")
    public ResponseEntity<ApiResponse<ExhibitorRegistrationResponseDTO>> getPaymentStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID registrationUuid) {
        ExhibitorRegistrationResponseDTO response = exhibitorRegistrationService.getRegistrationDetails(
                registrationUuid,
                userDetails.getUser().getId());
        return ok(response);
    }

    @PutMapping("/{uuid}/cancel")
    @Operation(summary = "Hủy đơn đăng ký triển lãm", description = "Chuyển trạng thái đơn đăng ký thành CANCELED và hủy bỏ các thanh toán liên quan. Chỉ cho phép khi đơn ở trạng thái PENDING hoặc PENDING_PAYMENT.")
    public ResponseEntity<ApiResponse<ExhibitorRegistrationResponseDTO>> cancelRegistration(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID registrationUuid) {
        ExhibitorRegistrationResponseDTO response = exhibitorRegistrationService.cancelRegistration(
                userDetails.getUser(), registrationUuid);
        return ok(response);
    }
}
