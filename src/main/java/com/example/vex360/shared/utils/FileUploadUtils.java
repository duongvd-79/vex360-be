package com.example.vex360.shared.utils;

import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import java.util.Arrays;
import java.util.List;

public class FileUploadUtils {

    // Allowed file types (MIME types)
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "image/webp", "image/svg+xml", "image/tiff",
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/x-ms-wmv", "video/x-flv",
            "video/webm");

    /**
     * Validation file size
     */
    public static void validateFileSize(MultipartFile file, long maxFileSizeInMB) {
        long fileSizeInBytes = file.getSize();
        long maxSizeBytes = maxFileSizeInMB * 1024 * 1024;
        if (fileSizeInBytes > maxSizeBytes) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * Validate file type
     */
    public static void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * Validate panorama ratio
     */
    public static void validatePanoramaRatio(int width, int height) {
        if (width != 2 * height) {
            throw new AppException(ErrorCode.NOT_A_PANORAMA);
        }
    }

    /**
     * Get file extension from filename
     */
    public static String getFileExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * Get file type from file extension
     */
    public static String getFileType(String extension) {
        if (extension == null) {
            return null;
        }
        extension = extension.toLowerCase();
        if (Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "tiff", "heic", "heif")
                .contains(extension)) {
            return "image";
        }
        if (Arrays.asList("mp4", "mov", "avi", "mkv", "wmv", "flv", "webm").contains(extension)) {
            return "video";
        }
        return "other";
    }

    /**
     * Generate unique filename
     */
    public static String generateUniqueFilename(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int) (Math.random() * 10000));
        return "upload_" + timestamp + "_" + random + "." + extension;
    }

    /**
     * Generate folder name based on file type and date
     */
    public static String generateFolderName(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String type = getFileType(extension);
        String date = java.time.LocalDate.now().toString();
        return type + "/" + date;
    }
}
