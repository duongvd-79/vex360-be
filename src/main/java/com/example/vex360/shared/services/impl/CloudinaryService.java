package com.example.vex360.shared.services.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService implements CloudService {
    private final Cloudinary cloudinary;

    @Override
    @Transactional
    public CloudinaryResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        // Validate type and size (10MB limit)
        FileUploadUtils.validateFileType(file);
        FileUploadUtils.validateFileSize(file, 10);
        log.info("File received: {}", file);

        try {
            String folder = FileUploadUtils.generateFolderName(file);
            Map<?, ?> params = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto");

            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    params);

            log.info("Successfully uploaded file {} to Cloudinary. Public ID: {}",
                    file.getOriginalFilename(), uploadResult.get("public_id"));

            String publicId = (String) uploadResult.get("public_id");
            Integer width = (Integer) uploadResult.get("width");
            Integer height = (Integer) uploadResult.get("height");
            String resourceType = (String) uploadResult.get("resource_type");
            if (resourceType == null || resourceType.isBlank()) {
                resourceType = "image";
            }

            String url = cloudinary.url()
                    .secure(true)
                    .resourceType(resourceType)
                    .transformation(new Transformation()
                            .quality("auto")
                            .fetchFormat("auto"))
                    .generate(publicId);

            return CloudinaryResponse.builder()
                    .url(url)
                    .publicId(publicId)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .fileType(file.getContentType())
                    .width(width)
                    .height(height)
                    .build();
        } catch (Exception e) {
            log.error("Failed to upload file {} to Cloudinary", file.getOriginalFilename(), e);
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public void delete(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            Map<?, ?> params = ObjectUtils.asMap(
                    "resource_type", resolveResourceType(resourceType));
            cloudinary.uploader().destroy(publicId, params);
            log.info("Deleted Cloudinary asset. Public ID: {}", publicId);
        } catch (Exception e) {
            log.error("Failed to delete Cloudinary asset {}", publicId, e);
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    private String resolveResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return "image";
        }
        return resourceType.toLowerCase();
    }
}
