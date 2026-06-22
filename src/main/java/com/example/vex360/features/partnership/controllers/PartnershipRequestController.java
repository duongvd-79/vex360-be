package com.example.vex360.features.partnership.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/partnership-requests")
@RequiredArgsConstructor
@Tag(name = "Partnership Requests", description = "Gửi yêu cầu hợp tác để trở thành EXHIBITOR hoặc ORGANIZER")
public class PartnershipRequestController {
    private final PartnershipRequestService partnershipRequestService;

    @PostMapping("/guest")
    @Operation(
            summary = "Guest gửi yêu cầu hợp tác",
            description = "Endpoint public cho khách chưa có tài khoản. Email không được tồn tại trong users.email. API chỉ tạo partnership request PENDING, chưa tạo user hoặc company.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> submitGuestRequest(
            @Valid @RequestBody SubmitPartnershipRequest request) {
        PartnershipRequestResponseDTO partnershipRequest = partnershipRequestService.submitGuestRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(partnershipRequest));
    }

    @PostMapping
    @Operation(
            summary = "User đã đăng nhập gửi yêu cầu hợp tác",
            description = "Endpoint authenticated. Request được gắn với current user; requesterEmail có thể khác email tài khoản để admin kiểm tra thủ công khi duyệt.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> submitAuthenticatedRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SubmitPartnershipRequest request) {
        PartnershipRequestResponseDTO partnershipRequest = partnershipRequestService.submitAuthenticatedRequest(
                userDetails.getUser(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(partnershipRequest));
    }
}
