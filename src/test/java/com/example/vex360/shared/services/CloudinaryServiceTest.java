package com.example.vex360.shared.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.Uploader;
import com.cloudinary.Url;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.impl.CloudinaryService;
import com.example.vex360.shared.utils.FileUploadUtils;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    private MockMultipartFile validFile;

    @BeforeEach
    void setUp() {
        validFile = new MockMultipartFile(
                "file", "test.png", "image/png", "dummy-image-content".getBytes()
        );
    }

    @Test
    void uploadSuccessReturnsCloudinaryResponse() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("secure_url", "https://cloudinary.com/test.png");
        mockResult.put("public_id", "test_public_id");
        mockResult.put("width", 800);
        mockResult.put("height", 600);
        mockResult.put("resource_type", "image");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        Url mockUrl = mock(Url.class);
        when(cloudinary.url()).thenReturn(mockUrl);
        when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.resourceType(anyString())).thenReturn(mockUrl);
        when(mockUrl.transformation(any(Transformation.class))).thenReturn(mockUrl);
        when(mockUrl.generate(anyString())).thenReturn("https://cloudinary.com/test_public_id.png");

        CloudinaryResponse response = cloudinaryService.upload(validFile);

        assertNotNull(response);
        assertEquals("https://cloudinary.com/test_public_id.png", response.getUrl());
        assertEquals("test_public_id", response.getPublicId());
        assertEquals("test.png", response.getFileName());
        assertEquals(800, response.getWidth());
        assertEquals(600, response.getHeight());
    }

    @Test
    void uploadEmptyFileThrowsFileTypeNotSupported() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "image/png", new byte[0]);

        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.upload(emptyFile);
        });

        assertEquals(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
    }

    @Test
    void uploadNullFileThrowsFileTypeNotSupported() {
        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.upload(null);
        });

        assertEquals(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
    }

    @Test
    void uploadUnsupportedFileTypeThrowsFileTypeNotSupported() {
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "dummy text".getBytes()
        );

        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.upload(unsupportedFile);
        });

        assertEquals(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
    }

    @Test
    void uploadFileSizeExceededThrowsFileSizeExceeded() {
        // Enforce > 10MB limit in test
        // 11MB = 11 * 1024 * 1024 bytes
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.png", "image/png", largeBytes
        );

        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.upload(largeFile);
        });

        assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void uploadSdkFailureThrowsUploadFailed() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("Cloudinary is down"));

        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.upload(validFile);
        });

        assertEquals(ErrorCode.UPLOAD_FAILED, exception.getErrorCode());
    }

    @Test
    void deleteImageUsesImageResourceType() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        cloudinaryService.delete("image-public-id", "image");

        verify(uploader).destroy(eq("image-public-id"), anyMap());
    }

    @Test
    void deleteVideoUsesVideoResourceType() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        cloudinaryService.delete("video-public-id", "video");

        verify(uploader).destroy(eq("video-public-id"), anyMap());
    }

    @Test
    void deleteSdkFailureThrowsUploadFailed() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("public-id"), anyMap())).thenThrow(new IOException("Cloudinary is down"));

        AppException exception = assertThrows(AppException.class, () -> {
            cloudinaryService.delete("public-id", "image");
        });

        assertEquals(ErrorCode.UPLOAD_FAILED, exception.getErrorCode());
    }

    @Test
    void uploadPanoramaValidJpegSuccess() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile jpegFile = new MockMultipartFile(
                "file", "pano.jpg", "image/jpeg", "dummy-jpeg-bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "pano_public_id");
        mockResult.put("width", 4096);
        mockResult.put("height", 2048);
        mockResult.put("format", "jpg");
        mockResult.put("resource_type", "image");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        Url mockUrl = mock(Url.class);
        when(cloudinary.url()).thenReturn(mockUrl);
        when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.resourceType(anyString())).thenReturn(mockUrl);
        when(mockUrl.transformation(any(Transformation.class))).thenReturn(mockUrl);
        when(mockUrl.generate(anyString())).thenReturn("https://cloudinary.com/pano_public_id.jpg");

        CloudinaryResponse response = cloudinaryService.uploadToFolder(jpegFile, FileUploadUtils.PANORAMA_FOLDER);

        assertNotNull(response);
        assertEquals("image/jpeg", response.getFileType());
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @Test
    void uploadPanoramaValidWebpSuccess() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile webpFile = new MockMultipartFile(
                "file", "pano.webp", "image/webp", "dummy-webp-bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "pano_webp_id");
        mockResult.put("width", 2048);
        mockResult.put("height", 1024);
        mockResult.put("format", "webp");
        mockResult.put("resource_type", "image");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        Url mockUrl = mock(Url.class);
        when(cloudinary.url()).thenReturn(mockUrl);
        when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.resourceType(anyString())).thenReturn(mockUrl);
        when(mockUrl.transformation(any(Transformation.class))).thenReturn(mockUrl);
        when(mockUrl.generate(anyString())).thenReturn("https://cloudinary.com/pano_webp_id.webp");

        CloudinaryResponse response = cloudinaryService.uploadToFolder(webpFile, FileUploadUtils.PANORAMA_FOLDER);

        assertNotNull(response);
        assertEquals("image/webp", response.getFileType());
    }

    @Test
    void uploadPanoramaNullOrEmptyFileThrowsPanoramaFileRequired() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "image/png", new byte[0]);

        AppException ex1 = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(null, FileUploadUtils.PANORAMA_FOLDER));
        assertEquals(ErrorCode.PANORAMA_FILE_REQUIRED, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(emptyFile, FileUploadUtils.PANORAMA_FOLDER));
        assertEquals(ErrorCode.PANORAMA_FILE_REQUIRED, ex2.getErrorCode());
    }

    @Test
    void uploadPanoramaUnsupportedDeclaredMimeThrowsFormatNotSupported() {
        MockMultipartFile mp4File = new MockMultipartFile("file", "video.mp4", "video/mp4", "bytes".getBytes());
        MockMultipartFile svgFile = new MockMultipartFile("file", "vector.svg", "image/svg+xml", "bytes".getBytes());

        AppException ex1 = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(mp4File, FileUploadUtils.PANORAMA_FOLDER));
        assertEquals(ErrorCode.PANORAMA_FORMAT_NOT_SUPPORTED, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(svgFile, FileUploadUtils.PANORAMA_FOLDER));
        assertEquals(ErrorCode.PANORAMA_FORMAT_NOT_SUPPORTED, ex2.getErrorCode());
    }

    @Test
    void uploadPanoramaActualResourceVideoTriggersCleanupAndThrowsInvalidContent() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "video_id");
        mockResult.put("resource_type", "video");
        mockResult.put("format", "mp4");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID, ex.getErrorCode());
        verify(uploader).destroy(eq("video_id"), anyMap());
    }

    @Test
    void uploadPanoramaActualFormatUnsupportedTriggersCleanupAndThrowsInvalidContent() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "avif_id");
        mockResult.put("resource_type", "image");
        mockResult.put("format", "avif");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID, ex.getErrorCode());
        verify(uploader).destroy(eq("avif_id"), anyMap());
    }

    @Test
    void uploadPanoramaMissingDimensionsTriggersCleanupAndThrowsInvalidContent() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "nodim_id");
        mockResult.put("resource_type", "image");
        mockResult.put("format", "jpg");
        mockResult.put("width", null);
        mockResult.put("height", 1024);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_IMAGE_CONTENT_INVALID, ex.getErrorCode());
        verify(uploader).destroy(eq("nodim_id"), anyMap());
    }

    @Test
    void uploadPanoramaInvalidRatioTriggersCleanupAndThrowsAspectRatioInvalid() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "bad_ratio_id");
        mockResult.put("resource_type", "image");
        mockResult.put("format", "jpg");
        mockResult.put("width", 800);
        mockResult.put("height", 600);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_ASPECT_RATIO_INVALID, ex.getErrorCode());
        verify(uploader).destroy(eq("bad_ratio_id"), anyMap());
    }

    @Test
    void uploadPanoramaExceededResolutionTriggersCleanupAndThrowsResolutionExceeded() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "huge_id");
        mockResult.put("resource_type", "image");
        mockResult.put("format", "jpg");
        mockResult.put("width", 16384);
        mockResult.put("height", 8192);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_RESOLUTION_EXCEEDED, ex.getErrorCode());
        verify(uploader).destroy(eq("huge_id"), anyMap());
    }

    @Test
    void uploadPanoramaCleanupFailureDoesNotMaskValidationError() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile file = new MockMultipartFile("file", "pano.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "cleanup_fail_id");
        mockResult.put("resource_type", "image");
        mockResult.put("format", "jpg");
        mockResult.put("width", 800);
        mockResult.put("height", 600);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);
        when(uploader.destroy(eq("cleanup_fail_id"), anyMap())).thenThrow(new IOException("Destroy failed"));

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadToFolder(file, FileUploadUtils.PANORAMA_FOLDER));

        assertEquals(ErrorCode.PANORAMA_ASPECT_RATIO_INVALID, ex.getErrorCode());
    }

    @Test
    void uploadNonPanoramaFolderDoesNotEnforceRatio() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);

        MockMultipartFile avatarFile = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "bytes".getBytes());

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("public_id", "avatar_id");
        mockResult.put("resource_type", "image");
        mockResult.put("width", 800);
        mockResult.put("height", 600);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        Url mockUrl = mock(Url.class);
        when(cloudinary.url()).thenReturn(mockUrl);
        when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.resourceType(anyString())).thenReturn(mockUrl);
        when(mockUrl.transformation(any(Transformation.class))).thenReturn(mockUrl);
        when(mockUrl.generate(anyString())).thenReturn("https://cloudinary.com/avatar_id.jpg");

        CloudinaryResponse response = cloudinaryService.uploadToFolder(avatarFile, "avatars");

        assertNotNull(response);
        assertEquals(800, response.getWidth());
        assertEquals(600, response.getHeight());
    }
}
