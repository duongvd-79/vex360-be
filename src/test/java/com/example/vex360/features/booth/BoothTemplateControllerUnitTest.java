package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.controllers.BoothTemplateController;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.CreatePanoramaRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothTemplateService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BoothTemplateControllerUnitTest {
    @Mock
    private BoothTemplateService boothTemplateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID boothId;
    private BoothTemplateResponseDTO response;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BoothTemplateController(boothTemplateService))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        boothId = UUID.randomUUID();
        response = new BoothTemplateResponseDTO(
                boothId,
                "Template A",
                "Demo",
                BoothStatus.DRAFT,
                true,
                UUID.randomUUID(),
                "admin@example.com",
                null,
                null,
                List.of(new PanoramaResponseDTO(UUID.randomUUID(), "Entrance", "/uploads/panoramas/a.jpg", "a.jpg", 0, true, List.of())));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBoothTemplateReturnsApiResponseAndRemovesMetadataFromFiles() throws Exception {
        authenticateAdmin();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template A",
                "Demo",
                BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, null)));
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));
        MockMultipartFile panoramaFile = new MockMultipartFile(
                "file_1",
                "entrance.jpg",
                "image/jpeg",
                "image".getBytes());

        when(boothTemplateService.createBoothTemplate(any(User.class), any(CreateBoothTemplateRequest.class), anyMap()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/booths/templates")
                .file(metadata)
                .file(panoramaFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Template A"))
                .andExpect(jsonPath("$.data.panoramas[0].name").value("Entrance"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, MultipartFile>> filesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(boothTemplateService).createBoothTemplate(any(User.class), any(CreateBoothTemplateRequest.class), filesCaptor.capture());
        assertTrue(filesCaptor.getValue().containsKey("file_1"));
        assertFalse(filesCaptor.getValue().containsKey("metadata"));
    }

    @Test
    void getBoothTemplatesReturnsPagedApiResponse() throws Exception {
        BoothTemplateSummaryResponseDTO summary = new BoothTemplateSummaryResponseDTO(
                boothId,
                "Template A",
                "Demo",
                BoothStatus.DRAFT,
                true,
                1,
                null,
                null);
        PageResponse<BoothTemplateSummaryResponseDTO> pageResponse = PageResponse.<BoothTemplateSummaryResponseDTO>builder()
                .content(List.of(summary))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(boothTemplateService.getBoothTemplates(any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/booths/templates?keyword=Template&status=DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Template A"))
                .andExpect(jsonPath("$.data.content[0].panoramaCount").value(1));
    }

    @Test
    void getBoothTemplateByIdReturnsDetailApiResponse() throws Exception {
        when(boothTemplateService.getBoothTemplateById(boothId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/booths/templates/{id}", boothId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(boothId.toString()))
                .andExpect(jsonPath("$.data.panoramas[0].imageUrl").value("/uploads/panoramas/a.jpg"));
    }

    private void authenticateAdmin() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
