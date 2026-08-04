package com.example.vex360.features.exhibition.controllers;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.dtos.response.PaymentHistoryResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitorPaymentService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exhibitor/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXHIBITOR')")
@RequireActiveCompany(roles = Role.EXHIBITOR)
@Tag(name = "Exhibitor Payments", description = "Lịch sử thanh toán của đơn vị triển lãm")
public class ExhibitorPaymentController extends BaseController {

    private final ExhibitorPaymentService exhibitorPaymentService;

    @GetMapping
    @Operation(summary = "Xem lịch sử thanh toán")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponseDTO>>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(exhibitorPaymentService.getHistory(userDetails.getUser(), pageable));
    }
}
