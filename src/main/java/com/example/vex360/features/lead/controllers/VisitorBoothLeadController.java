package com.example.vex360.features.lead.controllers;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.lead.dtos.request.CreateBoothLeadRequest;
import com.example.vex360.features.lead.dtos.response.BoothLeadSubmissionResponseDTO;
import com.example.vex360.features.lead.dtos.response.BoothLeadSubmissionStatusDTO;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/visitor/exhibitions/{exhibitionUuid}/booths/{boothId}/lead")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VISITOR')")
public class VisitorBoothLeadController extends BaseController {

    private final BoothLeadService boothLeadService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<BoothLeadSubmissionStatusDTO>> getMySubmissionStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId) {
        return ok(boothLeadService.getSubmissionStatus(
                userDetails.getUser(),
                exhibitionUuid,
                boothId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BoothLeadSubmissionResponseDTO>> submitLead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID exhibitionUuid,
            @PathVariable UUID boothId,
            @Valid @RequestBody CreateBoothLeadRequest request) {
        BoothLeadSubmissionResponseDTO response = boothLeadService.submitLead(
                userDetails.getUser(),
                exhibitionUuid,
                boothId,
                request);
        return response.alreadySubmitted()
                ? ok(response, "Đã cập nhật thông tin quan tâm của bạn.")
                : created(response, "Đã gửi thông tin cho gian hàng.");
    }
}
