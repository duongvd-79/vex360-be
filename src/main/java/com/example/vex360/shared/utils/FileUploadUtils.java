package com.example.vex360.shared.utils;

import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class FileUploadUtils {
    private FileUploadUtils() {
    }
    private static final Random r = new Random();

    /** Cloudinary folder prefix for panorama images */
    public static final String PANORAMA_FOLDER = "panorama";

    private static final Set<String> PANORAMA_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    // Allowed file types (MIME types)
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "image/webp", "image/svg+xml", "image/tiff",
            "audio/mpeg", "audio/mp3",
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/x-ms-wmv", "video/x-flv",
            "video/webm");

    /**
     * Pre-upload validation for panorama files
     */
    public static void validatePanoramaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.PANORAMA_FILE_REQUIRED);
        }
        validateFileSize(file, 10);
        String contentType = file.getContentType();
        if (contentType == null || !PANORAMA_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.PANORAMA_FORMAT_NOT_SUPPORTED);
        }
    }

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
            throw new AppException(ErrorCode.PANORAMA_ASPECT_RATIO_INVALID);
        }
    }

    /**
     * Validate panorama dimensions and resolution limit
     */
    public static void validatePanoramaDimensions(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new AppException(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID);
        }
        validatePanoramaRatio(width, height);
        long pixels = (long) width * height;
        if (width > 8192 || height > 4096 || pixels > 33_554_432L) {
            throw new AppException(ErrorCode.PANORAMA_RESOLUTION_EXCEEDED);
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
        if ("mp3".equals(extension)) {
            return "audio";
        }
        return "other";
    }

    /**
     * Generate unique filename
     */
    public static String generateUniqueFilename(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf(r.nextInt(10000));
        return "upload_" + timestamp + "_" + random + "." + extension;
    }

    /**
     * Generate folder name based on file type and date
     */
    public static String generateFolderName(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String type = getFileType(extension);
        String date = LocalDate.now().toString();
        return type + "/" + date;
    }
}
