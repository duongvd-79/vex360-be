package com.example.vex360.features.booth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.booth.controllers.ExhibitorBoothController;
import com.example.vex360.features.booth.controllers.ExhibitorBoothTemplateController;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.shared.dtos.PageResponse;

class ExhibitorBoothTemplateControllerUnitTest {
    private ExhibitorBoothTemplateService templateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        templateService = mock(ExhibitorBoothTemplateService.class);
        ExhibitorBoothController boothController = new ExhibitorBoothController(
                mock(ExhibitorBoothService.class),
                templateService,
                mock(ExhibitorPanoramaService.class),
                mock(ExhibitorHotspotService.class),
                mock(BoothReviewService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ExhibitorBoothTemplateController(templateService),
                boothController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void publishedTemplateRoutesAreExposed() throws Exception {
        UUID templateId = UUID.randomUUID();
        PageResponse<ExhibitorBoothTemplateSummaryResponseDTO> page =
                PageResponse.<ExhibitorBoothTemplateSummaryResponseDTO>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();
        ExhibitorBoothTemplateResponseDTO detail = new ExhibitorBoothTemplateResponseDTO(
                templateId,
                "Modern",
                null,
                null,
                1L,
                0L,
                List.of());
        when(templateService.getPublishedTemplates(eq("modern"), any(Pageable.class))).thenReturn(page);
        when(templateService.getPublishedTemplate(templateId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/exhibitor/booth-templates").param("keyword", "modern"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
        mockMvc.perform(get("/api/v1/exhibitor/booth-templates/{templateId}", templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(templateId.toString()));

        verify(templateService).getPublishedTemplates(eq("modern"), any(Pageable.class));
        verify(templateService).getPublishedTemplate(templateId);
    }

    @Test
    void oldBoothScopedGetRoutesAreNotExposed() throws Exception {
        UUID boothId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/exhibitor/booths/{boothId}/templates", boothId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                "/api/v1/exhibitor/booths/{boothId}/templates/{templateId}",
                boothId,
                templateId))
                .andExpect(status().isNotFound());
    }
}
