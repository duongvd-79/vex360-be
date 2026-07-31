package com.example.vex360.shared.services.impl;

import java.util.Locale;
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
import com.example.vex360.shared.utils.LogSanitizer;

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
            Integer width = toInteger(uploadResult.get("width"));
            Integer height = toInteger(uploadResult.get("height"));
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
    public CloudinaryResponse uploadToFolder(MultipartFile file, String folder) {
        boolean panoramaUpload = FileUploadUtils.PANORAMA_FOLDER.equals(folder);

        if (panoramaUpload) {
            FileUploadUtils.validatePanoramaFile(file);
        } else {
            if (file == null || file.isEmpty()) {
                throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
            }
            FileUploadUtils.validateFileType(file);
            FileUploadUtils.validateFileSize(file, 10);
        }
        log.info("File received for folder [{}]: {}", folder, file.getOriginalFilename());

        String publicId = null;
        String resourceType = null;
        try {
            String date = java.time.LocalDate.now().toString();
            String targetFolder = (folder != null && !folder.isBlank())
                    ? folder + "/" + date
                    : FileUploadUtils.generateFolderName(file);

            Map<?, ?> params = ObjectUtils.asMap(
                    "folder", targetFolder,
                    "resource_type", panoramaUpload ? "image" : "auto");

            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

            publicId = (String) uploadResult.get("public_id");
            resourceType = (String) uploadResult.get("resource_type");
            Integer width = toInteger(uploadResult.get("width"));
            Integer height = toInteger(uploadResult.get("height"));
            String format = (String) uploadResult.get("format");

            String mimeType;
            if (panoramaUpload) {
                if (!"image".equalsIgnoreCase(resourceType)) {
                    throw new AppException(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID);
                }

                mimeType = panoramaMimeType(format == null ? "" : format.toLowerCase(Locale.ROOT));
                FileUploadUtils.validatePanoramaDimensions(width, height);
            } else {
                mimeType = file.getContentType();
            }

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

            log.info("Successfully uploaded file {} to Cloudinary folder [{}]. Public ID: {}",
                    file.getOriginalFilename(), targetFolder, publicId);

            return CloudinaryResponse.builder()
                    .url(url)
                    .publicId(publicId)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .fileType(mimeType)
                    .width(width)
                    .height(height)
                    .build();
        } catch (AppException e) {
            if (panoramaUpload) {
                cleanupRejectedUpload(publicId, resourceType);
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file {} to Cloudinary folder [{}]",
                    file != null ? file.getOriginalFilename() : "null",
                    folder, e);
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

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String panoramaMimeType(String format) {
        return switch (format) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new AppException(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID);
        };
    }

    private void cleanupRejectedUpload(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type",
                            resourceType == null || resourceType.isBlank() ? "image" : resourceType));
        } catch (Exception cleanupException) {
            log.error("Failed to clean rejected Cloudinary upload {} ({})",
                    LogSanitizer.sanitize(publicId),
                    cleanupException.getClass().getSimpleName());
        }
    }

    private String resolveResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return "image";
        }
        return resourceType.toLowerCase();
    }
}
