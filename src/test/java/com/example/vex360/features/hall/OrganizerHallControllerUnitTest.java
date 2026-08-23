package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.hall.controllers.OrganizerHallController;
import com.example.vex360.features.hall.dtos.request.CreateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.request.UpdateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.response.ExhibitionHallResponseDTO;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallHotspotService;
import com.example.vex360.features.hall.services.OrganizerHallItemService;
import com.example.vex360.features.hall.services.OrganizerHallMediaAssetService;
import com.example.vex360.features.hall.services.OrganizerHallPanoramaService;
import com.example.vex360.features.hall.services.HallReviewSnapshot;
import com.example.vex360.features.hall.services.OrganizerHallPreviewService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.enums.Role;

@ExtendWith(MockitoExtension.class)
class OrganizerHallControllerUnitTest {
    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private OrganizerHallPanoramaService panoramaService;
    @Mock
    private OrganizerHallHotspotService hotspotService;
    @Mock
    private OrganizerHallItemService itemService;
    @Mock
    private OrganizerHallMediaAssetService mediaAssetService;
    @Mock
    private OrganizerHallPreviewService previewService;

    private OrganizerHallController controller;
    private CustomUserDetails userDetails;
    private User organizer;
    private UUID exhibitionUuid;
    private ExhibitionHallResponseDTO hallResponse;

    @BeforeEach
    void setUp() {
        controller = new OrganizerHallController(
                hallService,
                panoramaService,
                hotspotService,
                itemService,
                mediaAssetService,
                previewService);
        organizer = User.builder().id(UUID.randomUUID()).role(Role.ORGANIZER).build();
        userDetails = new CustomUserDetails(organizer);
        exhibitionUuid = UUID.randomUUID();
        hallResponse = ExhibitionHallResponseDTO.builder()
                .id(UUID.randomUUID())
                .exhibitionUuid(exhibitionUuid)
                .name("Hall")
                .status(HallStatus.DRAFT)
                .build();
    }

    @Test
    void createReadAndUpdateEndpointsDelegateToHallService() {
        CreateExhibitionHallRequest createRequest = new CreateExhibitionHallRequest("Hall", null);
        UpdateExhibitionHallRequest updateRequest = new UpdateExhibitionHallRequest("Updated Hall", "Description");
        when(hallService.createHall(organizer, exhibitionUuid, createRequest)).thenReturn(hallResponse);
        when(hallService.getHall(organizer, exhibitionUuid)).thenReturn(hallResponse);
        when(hallService.updateHall(organizer, exhibitionUuid, updateRequest)).thenReturn(hallResponse);

        ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> created =
                controller.createHall(userDetails, exhibitionUuid, createRequest);
        ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> read =
                controller.getHall(userDetails, exhibitionUuid);
        ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> updated =
                controller.updateHall(userDetails, exhibitionUuid, updateRequest);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.OK, read.getStatusCode());
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(hallResponse, created.getBody().data());
        assertEquals(hallResponse, read.getBody().data());
        assertEquals(hallResponse, updated.getBody().data());
        verify(hallService).createHall(organizer, exhibitionUuid, createRequest);
        verify(hallService).getHall(organizer, exhibitionUuid);
        verify(hallService).updateHall(organizer, exhibitionUuid, updateRequest);
    }

    @Test
    void previewEndpointReturnsCurrentDraftSnapshot() {
        HallReviewSnapshot preview = HallReviewSnapshot.builder()
                .snapshotSchemaVersion(1)
                .build();
        when(previewService.getPreview(organizer, exhibitionUuid)).thenReturn(preview);

        ResponseEntity<ApiResponse<HallReviewSnapshot>> response =
                controller.getPreview(userDetails, exhibitionUuid);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(preview, response.getBody().data());
        verify(previewService).getPreview(organizer, exhibitionUuid);
    }

    @Test
    void backgroundMusicEndpointsDelegateToHallService() {
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", "music".getBytes());
        when(hallService.updateBackgroundMusic(organizer, exhibitionUuid, music))
                .thenReturn(hallResponse);
        when(hallService.deleteBackgroundMusic(organizer, exhibitionUuid))
                .thenReturn(hallResponse);

        ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> updated =
                controller.updateBackgroundMusic(userDetails, exhibitionUuid, music);
        ResponseEntity<ApiResponse<ExhibitionHallResponseDTO>> deleted =
                controller.deleteBackgroundMusic(userDetails, exhibitionUuid);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(HttpStatus.OK, deleted.getStatusCode());
        verify(hallService).updateBackgroundMusic(organizer, exhibitionUuid, music);
        verify(hallService).deleteBackgroundMusic(organizer, exhibitionUuid);
    }
}
