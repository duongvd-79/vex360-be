package com.example.vex360.shared.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    private final CloudService cloudService;

    @PostMapping
    @Operation(summary = "Tải lên tệp tin", description = "Tải lên hình ảnh hoặc video lên Cloudinary. Yêu cầu đăng nhập. Giới hạn dung lượng tệp tin tối đa là 10MB.")
    public ApiResponse<CloudinaryResponse> uploadFile(@RequestPart MultipartFile file) {
        log.info("File received: {}", file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        return createSuccessResponse(cloudService.upload(file), "Tải lên tệp tin thành công!");
    }
}
