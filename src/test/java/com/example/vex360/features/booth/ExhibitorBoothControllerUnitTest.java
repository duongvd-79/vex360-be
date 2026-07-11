package com.example.vex360.features.booth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.controllers.ExhibitorBoothController;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.user.entities.User;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothControllerUnitTest {
    @Mock
    private ExhibitorBoothService exhibitorBoothService;
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
}
