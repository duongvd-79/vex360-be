package com.example.vex360.features.exhibition.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping
    @Operation(summary = "Tạo triển lãm mới", description = "Tạo một sự kiện triển lãm mới và gán nhà tổ chức hiện tại làm người sở hữu.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> createExhibition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateExhibitionRequest request) {
        ExhibitionResponseDTO response = exhibitionService.createExhibition(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(response));
    }
}
