package com.example.vex360.shared.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.impl.CloudinaryService;

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

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(mockResult);

        CloudinaryResponse response = cloudinaryService.upload(validFile);

        assertNotNull(response);
        assertEquals("https://cloudinary.com/test.png", response.getUrl());
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
}
