package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothTemplateHotspotService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class BoothTemplateHotspotServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private HotspotRepository hotspotRepository;

    private BoothTemplateHotspotService service;
    private User admin;

    @BeforeEach
    void setup() {
        service = new BoothTemplateHotspotService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                Mappers.getMapper(BoothMapper.class));
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createHotspot_DraftStatus_Succeeds() {
        Booth booth = templateBooth();
        Panorama source = panorama(booth, "Source");
        Panorama target = panorama(booth, "Target");
        when(boothRepository.findTemplateByIdForUpdate(booth.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothId(source.getId(), booth.getId())).thenReturn(Optional.of(source));
        when(panoramaRepository.findByIdAndBoothId(target.getId(), booth.getId())).thenReturn(Optional.of(target));
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> inv.getArgument(0));

        HotspotResponseDTO response = service.createHotspot(
                admin,
                booth.getId(),
                source.getId(),
                new CreateBoothTemplateHotspotRequest("Go to Target", target.getId(), 1.0, 2.0, 3.0));

        assertEquals("Go to Target", response.getName());
    }

    @Test
    void updateHotspot_DraftStatus_Succeeds() {
        Booth booth = templateBooth();
        Panorama source = panorama(booth, "Source");
        Hotspot hotspot = Hotspot.builder()
                .id(UUID.randomUUID())
                .name("Old Hotspot")
                .sourcePanorama(source)
                .build();
        when(boothRepository.findTemplateByIdForUpdate(booth.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothId(source.getId(), booth.getId())).thenReturn(Optional.of(source));
        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspot.getId(), source.getId()))
                .thenReturn(Optional.of(hotspot));
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> inv.getArgument(0));

        HotspotResponseDTO response = service.updateHotspot(
                admin,
                booth.getId(),
                source.getId(),
                hotspot.getId(),
                new UpdateBoothTemplateHotspotRequest("New Hotspot", null, 4.0, 5.0, 6.0));

        assertEquals("New Hotspot", response.getName());
        assertEquals(4.0, response.getXPosition());
    }

    @Test
    void deleteHotspot_DraftStatus_Succeeds() {
        Booth booth = templateBooth();
        Panorama source = panorama(booth, "Source");
        Hotspot hotspot = Hotspot.builder()
                .id(UUID.randomUUID())
                .name("Hotspot")
                .sourcePanorama(source)
                .build();
        when(boothRepository.findTemplateByIdForUpdate(booth.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothId(source.getId(), booth.getId())).thenReturn(Optional.of(source));
        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspot.getId(), source.getId()))
                .thenReturn(Optional.of(hotspot));

        HotspotResponseDTO response = service.deleteHotspot(
                admin, booth.getId(), source.getId(), hotspot.getId());

        assertNotNull(response);
        verify(hotspotRepository).delete(hotspot);
    }

    private Booth templateBooth() {
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Template A")
                .status(BoothStatus.DRAFT)
                .isTemplate(true)
                .createdBy(admin)
                .build();
    }

    private Panorama panorama(Booth booth, String name) {
        return Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name(name)
                .orderIndex(0)
                .isDefault(false)
                .build();
    }
}
