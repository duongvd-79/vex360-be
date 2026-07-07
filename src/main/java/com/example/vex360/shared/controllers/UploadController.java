package com.example.vex360.shared.controllers;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads", description = "Quản lý tải lên tệp tin phương tiện")
@Slf4j
public class UploadController extends BaseController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");

    private final CloudService cloudService;
    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;

    @PostMapping
    @Operation(summary = "Tải lên file sản phẩm", description = "Ảnh JPG/PNG hoặc video MP4. Tối đa 10MB.")
    public ResponseEntity<ApiResponse<CloudinaryResponse>> uploadFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        Company company = companyService.getCompanyEntityForCurrentUser(userDetails.getUser());
        companyStorageService.checkQuota(company, file.getSize());

        log.info("File received: {}", file.getOriginalFilename());
        CloudinaryResponse response = cloudService.upload(file);

        companyStorageService.addUsage(company, file.getSize());

        return ok(response, "Tải lên tệp tin thành công!");
    }
}
