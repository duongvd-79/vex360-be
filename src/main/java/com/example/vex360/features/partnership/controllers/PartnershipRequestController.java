package com.example.vex360.features.partnership.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/partnership-requests")
@RequiredArgsConstructor
@Tag(name = "Partnership Requests", description = "Gui yeu cau hop tac de tro thanh EXHIBITOR hoac ORGANIZER")
public class PartnershipRequestController extends BaseController {
    private final PartnershipRequestService partnershipRequestService;

    @PostMapping("/guest")
    @Operation(
            summary = "Guest gui yeu cau hop tac",
            description = "Endpoint public cho khach chua co tai khoan. API chi tao partnership request PENDING, chua tao user hoac company.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> submitGuestRequest(
            @Valid @RequestBody SubmitPartnershipRequest request) {
        PartnershipRequestResponseDTO partnershipRequest = partnershipRequestService.submitGuestRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(partnershipRequest));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VISITOR')")
    @Operation(
            summary = "User da dang nhap gui yeu cau hop tac",
            description = "Endpoint authenticated. requesterEmail phai trung email tai khoan dang dang nhap. Neu muon dung email khac, vui long dang xuat va gui yeu cau voi tu cach guest.")
    public ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> submitAuthenticatedRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SubmitPartnershipRequest request) {
        PartnershipRequestResponseDTO partnershipRequest = partnershipRequestService.submitAuthenticatedRequest(
                userDetails.getUser(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(partnershipRequest));
    }
}
