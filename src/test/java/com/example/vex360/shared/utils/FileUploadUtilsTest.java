package com.example.vex360.shared.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileUploadUtilsTest {

    @Test
    void validateFileType_AcceptsMp3MimeTypes() {
        MockMultipartFile mpeg = new MockMultipartFile(
                "file", "ambient.mp3", "audio/mpeg", "music".getBytes());
        MockMultipartFile mp3 = new MockMultipartFile(
                "file", "ambient.mp3", "audio/mp3", "music".getBytes());

        assertDoesNotThrow(() -> FileUploadUtils.validateFileType(mpeg));
        assertDoesNotThrow(() -> FileUploadUtils.validateFileType(mp3));
        assertEquals("audio", FileUploadUtils.getFileType("mp3"));
    }
}
