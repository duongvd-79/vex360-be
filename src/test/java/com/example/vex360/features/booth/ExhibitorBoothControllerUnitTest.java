package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.controllers.ExhibitorBoothController;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.dtos.ApiResponse;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothControllerUnitTest {
    @Mock
    private ExhibitorBoothService exhibitorBoothService;
    @Mock
    private ExhibitorBoothTemplateService exhibitorBoothTemplateService;
    @Mock
    private ExhibitorPanoramaService exhibitorPanoramaService;
    @Mock
    private ExhibitorHotspotService exhibitorHotspotService;
    @Mock
    private BoothReviewService boothReviewService;

    private ExhibitorBoothController controller;
    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setup() {
        controller = new ExhibitorBoothController(
                exhibitorBoothService,
                exhibitorBoothTemplateService,
                exhibitorPanoramaService,
                exhibitorHotspotService,
                boothReviewService);
        user = User.builder().id(UUID.randomUUID()).build();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void updateBooth_ForwardsBackgroundMusicPart() {
        UUID boothId = UUID.randomUUID();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", "music".getBytes());
        BoothResponseDTO response = new BoothResponseDTO();
        when(exhibitorBoothService.updateBooth(user, boothId, null, null, music)).thenReturn(response);

        controller.updateBooth(userDetails, boothId, null, null, music);

        verify(exhibitorBoothService).updateBooth(user, boothId, null, null, music);
    }

    @Test
    void deleteBackgroundMusic_DelegatesToService() {
        UUID boothId = UUID.randomUUID();
        when(exhibitorBoothService.deleteBackgroundMusic(user, boothId)).thenReturn(new BoothResponseDTO());

        controller.deleteBackgroundMusic(userDetails, boothId);

        verify(exhibitorBoothService).deleteBackgroundMusic(user, boothId);
    }

    @Test
    void deleteAllPanoramas_DelegatesAndReturnsNullData() {
        UUID boothId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.deleteAllPanoramas(userDetails, boothId);

        verify(exhibitorPanoramaService).deleteAllPanoramas(user, boothId);
        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().data());
    }

    @Test
    void templateEndpointsDelegateToExhibitorTemplateService() {
        UUID boothId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        PageResponse<ExhibitorBoothTemplateSummaryResponseDTO> page = PageResponse.<ExhibitorBoothTemplateSummaryResponseDTO>builder()
                .content(List.of())
                .build();
        ExhibitorBoothTemplateResponseDTO detail = new ExhibitorBoothTemplateResponseDTO();
        BoothResponseDTO applied = new BoothResponseDTO();
        when(exhibitorBoothTemplateService.getCompatibleTemplates(user, boothId, "modern", pageable))
                .thenReturn(page);
        when(exhibitorBoothTemplateService.getCompatibleTemplate(user, boothId, templateId)).thenReturn(detail);
        when(exhibitorBoothTemplateService.applyTemplate(user, boothId, templateId)).thenReturn(applied);

        controller.getCompatibleTemplates(userDetails, boothId, "modern", pageable);
        controller.getCompatibleTemplate(userDetails, boothId, templateId);
        controller.applyTemplate(userDetails, boothId, templateId);

        verify(exhibitorBoothTemplateService).getCompatibleTemplates(user, boothId, "modern", pageable);
        verify(exhibitorBoothTemplateService).getCompatibleTemplate(user, boothId, templateId);
        verify(exhibitorBoothTemplateService).applyTemplate(user, boothId, templateId);
    }
}
