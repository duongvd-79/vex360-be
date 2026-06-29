package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.CreateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.CreatePanoramaRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothTemplateService;
import com.example.vex360.features.booth.services.PanoramaStorageService;
import com.example.vex360.features.booth.services.PanoramaStorageService.StoredPanoramaFile;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothTemplateServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private PanoramaStorageService panoramaStorageService;

    private BoothTemplateService boothTemplateService;
    private User admin;

    @BeforeEach
    void setup() {
        boothTemplateService = new BoothTemplateService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                panoramaStorageService,
                Mappers.getMapper(BoothMapper.class));
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createTemplateWithOnePanoramaSucceeds() {
        mockSaveFlow();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template A",
                "Demo",
                BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1")));

        assertEquals("Template A", response.getName());
        assertEquals(1, response.getPanoramas().size());
        assertEquals("Entrance", response.getPanoramas().get(0).getName());
        assertEquals(true, response.getPanoramas().get(0).getIsDefault());
        assertEquals(0, response.getPanoramas().get(0).getHotspots().size());
    }

    @Test
    void createTemplateWithConnectedPanoramasSucceeds() {
        mockSaveFlow();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template B",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest(
                                "pano_1",
                                "file_1",
                                "Entrance",
                                0,
                                true,
                                List.of(new CreateHotspotRequest("Go main", "pano_2", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", 1, false, List.of())));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2")));

        assertEquals(2, response.getPanoramas().size());
        assertEquals(1, response.getPanoramas().get(0).getHotspots().size());
        assertEquals("Main", response.getPanoramas().get(0).getHotspots().get(0).getTargetPanoramaName());
    }

    @Test
    void createTemplateThrowsWhenMultiplePanoramasHaveNoHotspot() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template C",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, List.of()),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.INVALID_PANORAMA_HOTSPOT, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenHotspotTargetMissing() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template D",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest(
                                "pano_1",
                                "file_1",
                                "Entrance",
                                null,
                                true,
                                List.of(new CreateHotspotRequest("Broken", "missing", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.INVALID_PANORAMA_HOTSPOT, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenMultipleDefaultPanoramas() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template E",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, List.of()),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, true, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.INVALID_BOOTH_TEMPLATE, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenPanoramaFileMissing() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template F",
                null,
                BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of()));

        assertSame(ErrorCode.PANORAMA_FILE_INVALID, exception.getErrorCode());
    }

    private void mockSaveFlow() {
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> {
            Booth booth = invocation.getArgument(0);
            booth.setId(UUID.randomUUID());
            return booth;
        });

        when(panoramaStorageService.store(any(MultipartFile.class))).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            return new StoredPanoramaFile("/uploads/panoramas/" + file.getOriginalFilename(), file.getOriginalFilename());
        });

        when(panoramaRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Panorama> panoramas = new ArrayList<>((List<Panorama>) invocation.getArgument(0));
            panoramas.forEach(panorama -> panorama.setId(UUID.randomUUID()));
            return panoramas;
        });

        when(hotspotRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Hotspot> hotspots = new ArrayList<>((List<Hotspot>) invocation.getArgument(0));
            hotspots.forEach(hotspot -> hotspot.setId(UUID.randomUUID()));
            return hotspots;
        });
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", "image".getBytes());
    }
}
