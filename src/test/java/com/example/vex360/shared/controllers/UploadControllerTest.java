package com.example.vex360.shared.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private CloudService cloudService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UploadController(cloudService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadFileSuccessReturnsApiResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", MediaType.IMAGE_PNG_VALUE, "dummy content".getBytes()
        );

        CloudinaryResponse response = CloudinaryResponse.builder()
                .url("https://cloudinary.com/test.png")
                .publicId("test_public_id")
                .fileName("test.png")
                .fileSize(13L)
                .fileType(MediaType.IMAGE_PNG_VALUE)
                .width(100)
                .height(100)
                .build();

        when(cloudService.upload(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Tải lên tệp tin thành công!"))
                .andExpect(jsonPath("$.data.url").value("https://cloudinary.com/test.png"))
                .andExpect(jsonPath("$.data.publicId").value("test_public_id"))
                .andExpect(jsonPath("$.data.fileName").value("test.png"));
    }

    @Test
    void uploadEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "", MediaType.IMAGE_PNG_VALUE, new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/uploads").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE-002"))
                .andExpect(jsonPath("$.message").value("File type not supported"));
    }
}
