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
import com.example.vex360.shared.dtos.PresignUploadRequest;
import com.example.vex360.shared.dtos.PresignedUploadResponse;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.services.R2StorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.vex360.shared.dtos.DeleteR2FolderRequest;
import com.example.vex360.shared.dtos.DeleteUploadRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads", description = "Quản lý tải lên tệp tin phương tiện")
@Slf4j
public class UploadController extends BaseController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");
    private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_PRESIGN_FILE_SIZE = 50L * 1024 * 1024;

    private final CloudService cloudService;
    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;
    private final R2StorageService r2StorageService;

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

    @PostMapping("/avatar")
    @Operation(summary = "Tải lên ảnh đại diện", description = "Ảnh JPG/PNG tối đa 10MB. Dành cho MỌI user đã đăng nhập; không tra công ty, không tính vào kho lưu trữ công ty.")
    public ResponseEntity<ApiResponse<CloudinaryResponse>> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!AVATAR_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        CloudinaryResponse response = cloudService.uploadToFolder(file, "avatar");
        return ok(response, "Tải lên ảnh đại diện thành công!");
    }

    @PostMapping("/presign")
    @Operation(summary = "Xin liên kết tải thẳng lên Cloudflare R2", description = "Trả về URL đã ký cho từng tệp để trình duyệt PUT thẳng lên R2; máy chủ không nhận nội dung tệp. Dùng cho mô hình 3D (.obj/.mtl/.glb) và ảnh đi kèm.")
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> presignUpload(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PresignUploadRequest request) {

        long declaredTotalSize = 0L;
        for (PresignUploadRequest.FileItem file : request.getFiles()) {
            if (file.getFileSize() > MAX_PRESIGN_FILE_SIZE) {
                throw new AppException(ErrorCode.FILE_TOO_LARGE);
            }
            declaredTotalSize += file.getFileSize();
        }

        Company company = companyService.getCompanyEntityForCurrentUser(userDetails.getUser());

        // Chặn sớm để khỏi tải lên rồi mới biết hết dung lượng. Con số này do client khai nên
        // không ràng buộc được gì; kho lưu trữ chỉ bị trừ thật khi lưu sản phẩm, lúc máy chủ
        // hỏi lại R2 kích thước thực của từng tệp.
        companyStorageService.checkQuota(company, declaredTotalSize);

        PresignedUploadResponse response = r2StorageService.presignBatch(company.getId(), request.getFiles());
        log.info("Đã ký {} liên kết tải lên cho công ty {}", response.getFiles().size(), company.getId());

        return ok(response, "Tạo liên kết tải lên thành công!");
    }

    @DeleteMapping
    @Operation(summary = "Xóa file đã upload (orphan cleanup)", description = "Xóa file khỏi Cloudinary và deduct storage khi user cancel form.")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteUploadRequest request) {
        Company company = companyService.getCompanyEntityForCurrentUser(userDetails.getUser());
        long deletedBytes = cloudService.deleteAndGetSize(request.getPublicId(), request.getResourceType());
        if (deletedBytes > 0) {
            companyStorageService.deductUsage(company, deletedBytes);
        }
        return ok(null, "Đã xóa file.");
    }


    @DeleteMapping("/r2")
    @Operation(summary = "Dọn thư mục tải lên chưa dùng", description = "Xoá cả thư mục đã tải lên R2 khi người dùng huỷ biểu mẫu, để tệp không nằm lại chiếm dung lượng. Chỉ xoá được thư mục thuộc doanh nghiệp của chính mình.")
    public ResponseEntity<ApiResponse<Void>> deleteR2Folder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteR2FolderRequest request) {
        Company company = companyService.getCompanyEntityForCurrentUser(userDetails.getUser());
        r2StorageService.deleteUploadFolder(company.getId(), request.getPrefix());
        return ok(null, "Đã dọn thư mục tải lên.");
    }
}
