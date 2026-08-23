package com.example.vex360.features.hall.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO;
import com.example.vex360.features.hall.services.HallVisitorService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/exhibitions")
@RequiredArgsConstructor
@Tag(name = "Public Exhibition Experience", description = "Trải nghiệm Hall 360 công khai")
public class PublicHallExperienceController extends BaseController {
    private final HallVisitorService hallVisitorService;

    @GetMapping("/{exhibitionUuid}/experience")
    @Operation(summary = "Lấy trải nghiệm 360 của triển lãm đang diễn ra")
    public ResponseEntity<ApiResponse<PublicExhibitionExperienceResponseDTO>> getExperience(
            @PathVariable UUID exhibitionUuid) {
        return ok(hallVisitorService.getExperience(exhibitionUuid));
    }
}
