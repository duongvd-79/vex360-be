package com.example.vex360.features.booth.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@Service
public class PanoramaStorageService {
    @Value("${app.upload.panorama-directory:uploads/panoramas}")
    private String panoramaDirectory;

    @Value("${app.upload.panorama-url-prefix:/uploads/panoramas}")
    private String panoramaUrlPrefix;

    public StoredPanoramaFile store(MultipartFile file) {
        validateImageFile(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "panorama" : file.getOriginalFilename());
        String imageKey = UUID.randomUUID() + resolveExtension(originalFilename);

        try {
            Path uploadDir = Paths.get(panoramaDirectory).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            Path targetPath = uploadDir.resolve(imageKey).normalize();
            if (!targetPath.startsWith(uploadDir)) {
                throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
            }

            file.transferTo(targetPath);
            return new StoredPanoramaFile(buildImageUrl(imageKey), imageKey);
        } catch (IOException e) {
            throw new AppException(ErrorCode.PANORAMA_FILE_SAVE_FAILED);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (extension.contains("/") || extension.contains("\\")) {
            return "";
        }
        return extension;
    }

    private String buildImageUrl(String imageKey) {
        String prefix = panoramaUrlPrefix.endsWith("/")
                ? panoramaUrlPrefix.substring(0, panoramaUrlPrefix.length() - 1)
                : panoramaUrlPrefix;
        return prefix + "/" + imageKey;
    }

    public record StoredPanoramaFile(String imageUrl, String imageKey) {
    }
}
