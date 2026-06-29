package com.example.vex360.features.exhibition.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/exhibitions")
@RequiredArgsConstructor
@Tag(name = "Public Exhibitions", description = "Các endpoint xem thông tin triển lãm công khai (không cần đăng nhập)")
public class PublicExhibitionController extends BaseController {

    private final ExhibitionService exhibitionService;

    @GetMapping("/{uuid}")
    @Operation(summary = "Xem thông tin triển lãm công khai theo UUID", description = "Lấy thông tin chi tiết triển lãm và danh sách gói dịch vụ tương ứng dựa trên UUID. Không để lộ khóa chính (Integer ID) của triển lãm trong payload.")
    public ResponseEntity<ApiResponse<ExhibitionResponseDTO>> getExhibitionByUuid(@PathVariable("uuid") UUID uuid) {
        ExhibitionResponseDTO response = exhibitionService.getExhibitionByUuid(uuid);
        return ok(response);
    }
}
