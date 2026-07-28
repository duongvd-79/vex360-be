package com.example.vex360.features.lead.controllers;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.lead.dtos.request.UpdateBoothLeadRequest;
import com.example.vex360.features.lead.dtos.response.BoothLeadResponseDTO;
import com.example.vex360.features.lead.dtos.response.BoothLeadSummaryDTO;
import com.example.vex360.features.lead.enums.LeadStatus;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exhibitor/leads")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
public class ExhibitorBoothLeadController extends BaseController {

    private final BoothLeadService boothLeadService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BoothLeadResponseDTO>>> getLeads(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) UUID boothId,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(boothLeadService.getLeads(
                userDetails.getUser(),
                boothId,
                status,
                keyword,
                pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BoothLeadSummaryDTO>> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) UUID boothId) {
        return ok(boothLeadService.getSummary(userDetails.getUser(), boothId));
    }

    @PatchMapping("/{leadId}")
    public ResponseEntity<ApiResponse<BoothLeadResponseDTO>> updateLead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateBoothLeadRequest request) {
        return ok(
                boothLeadService.updateLead(userDetails.getUser(), leadId, request),
                "Đã cập nhật trạng thái lead.");
    }
}
