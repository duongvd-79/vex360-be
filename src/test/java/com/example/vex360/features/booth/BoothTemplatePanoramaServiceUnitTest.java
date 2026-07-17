package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothTemplatePanoramaService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

@ExtendWith(MockitoExtension.class)
class BoothTemplatePanoramaServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private HotspotRepository hotspotRepository;
    @Mock
    private CloudService cloudService;
    @Mock
    private PanoramaImageCleanupService panoramaImageCleanupService;

    private BoothTemplatePanoramaService service;
    private User admin;

    @BeforeEach
    void setup() {
        service = new BoothTemplatePanoramaService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                cloudService,
                panoramaImageCleanupService,
                Mappers.getMapper(BoothMapper.class));
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createPanorama_DraftStatus_Succeeds() {
        Booth booth = templateBooth(BoothStatus.DRAFT);
        UUID boothId = booth.getId();
        when(boothRepository.findTemplateByIdForUpdate(boothId)).thenReturn(Optional.of(booth));

        MockMultipartFile image = new MockMultipartFile("image", "pano.jpg", "image/jpeg", "data".getBytes());
        CloudinaryResponse cloudResponse = CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/pano.jpg")
                .publicId("template/pano_1")
                .build();
        when(cloudService.uploadToFolder(any(), any())).thenReturn(cloudResponse);
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateExhibitorPanoramaRequest request = new CreateExhibitorPanoramaRequest("Pano Entrance", 1, true);
        PanoramaResponseDTO response = service.createPanorama(admin, boothId, request, image);

        assertNotNull(response);
        assertEquals("Pano Entrance", response.getName());
        assertEquals("https://res.cloudinary.com/pano.jpg", response.getImageUrl());
        assertEquals(1, response.getOrderIndex());
        assertEquals(true, response.getIsDefault());
        verify(cloudService).uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
    }

    @Test
    void createPanorama_WhenPersistenceFails_DeletesUploadedImage() {
        Booth booth = templateBooth(BoothStatus.DRAFT);
        when(boothRepository.findTemplateByIdForUpdate(booth.getId())).thenReturn(Optional.of(booth));
        MockMultipartFile image = new MockMultipartFile("image", "pano.jpg", "image/jpeg", "data".getBytes());
        CloudinaryResponse uploaded = CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/pano.jpg")
                .publicId("template/new_pano")
                .build();
        when(cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER)).thenReturn(uploaded);
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenThrow(new IllegalStateException("save failed"));

        assertThrows(IllegalStateException.class, () -> service.createPanorama(
                admin,
                booth.getId(),
                new CreateExhibitorPanoramaRequest("Entrance", 0, true),
                image));

        verify(cloudService).delete("template/new_pano", "image");
    }

    @Test
    void createPanorama_PublishedStatus_ThrowsException() {
        Booth booth = templateBooth(BoothStatus.PUBLISHED);
        UUID boothId = booth.getId();
        when(boothRepository.findTemplateByIdForUpdate(boothId)).thenReturn(Optional.of(booth));

        CreateExhibitorPanoramaRequest request = new CreateExhibitorPanoramaRequest("Pano Entrance", 1, true);
        assertThrows(AppException.class, () -> service.createPanorama(admin, boothId, request, null));
    }

    @Test
    void updatePanorama_DraftStatus_SchedulesOldImageCleanup() {
        Booth booth = templateBooth(BoothStatus.DRAFT);
        UUID boothId = booth.getId();
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = panorama(booth, "Entrance", "template/old_pano");

        when(boothRepository.findTemplateByIdForUpdate(boothId)).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, boothId))
                .thenReturn(Optional.of(panorama));
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile newImage = new MockMultipartFile(
                "image", "new_pano.jpg", "image/jpeg", "new_data".getBytes());
        when(cloudService.uploadToFolder(any(), any())).thenReturn(CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/new_pano.jpg")
                .publicId("template/new_pano")
                .build());

        PanoramaResponseDTO response = service.updatePanorama(
                admin,
                boothId,
                panoramaId,
                new UpdateExhibitorPanoramaRequest("New Entrance", 1, false),
                newImage);

        assertEquals("New Entrance", response.getName());
        verify(panoramaImageCleanupService).scheduleCleanup("template/old_pano");
    }

    @Test
    void updatePanorama_ArchivedStatus_SchedulesReferenceAwareCleanup() {
        Booth booth = templateBooth(BoothStatus.ARCHIVED);
        UUID boothId = booth.getId();
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = panorama(booth, "Entrance", "template/old_pano");

        when(boothRepository.findTemplateByIdForUpdate(boothId)).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, boothId))
                .thenReturn(Optional.of(panorama));
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile newImage = new MockMultipartFile(
                "image", "new_pano.jpg", "image/jpeg", "new_data".getBytes());
        when(cloudService.uploadToFolder(any(), any())).thenReturn(CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/new_pano.jpg")
                .publicId("template/new_pano")
                .build());

        PanoramaResponseDTO response = service.updatePanorama(
                admin,
                boothId,
                panoramaId,
                new UpdateExhibitorPanoramaRequest("New Entrance", 1, false),
                newImage);

        assertEquals("New Entrance", response.getName());
        verify(panoramaImageCleanupService).scheduleCleanup("template/old_pano");
    }

    @Test
    void deletePanorama_DraftStatus_SchedulesImageCleanup() {
        assertPanoramaDeletion(BoothStatus.DRAFT);
    }

    @Test
    void deletePanorama_ArchivedStatus_SchedulesImageCleanup() {
        assertPanoramaDeletion(BoothStatus.ARCHIVED);
    }

    @Test
    void deletePanorama_WhenTargetedByHotspot_RejectsWithoutDeleting() {
        Booth booth = templateBooth(BoothStatus.DRAFT);
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = panorama(booth, "Entrance", "template/old_pano");
        when(boothRepository.findTemplateByIdForUpdate(booth.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId()))
                .thenReturn(Optional.of(panorama));
        when(hotspotRepository.existsByTargetPanoramaId(panoramaId)).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deletePanorama(admin, booth.getId(), panoramaId));

        assertSame(ErrorCode.INVALID_PANORAMA_HOTSPOT, exception.getErrorCode());
        verify(panoramaRepository, never()).delete(any());
        verify(panoramaImageCleanupService, never()).scheduleCleanup(any(String.class));
    }

    private void assertPanoramaDeletion(BoothStatus status) {
        Booth booth = templateBooth(status);
        UUID boothId = booth.getId();
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = panorama(booth, "Entrance", "template/old_pano");
        when(boothRepository.findTemplateByIdForUpdate(boothId)).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, boothId))
                .thenReturn(Optional.of(panorama));

        PanoramaResponseDTO response = service.deletePanorama(admin, boothId, panoramaId);

        assertNotNull(response);
        verify(panoramaRepository).delete(panorama);
        verify(panoramaImageCleanupService).scheduleCleanup("template/old_pano");
    }

    private Booth templateBooth(BoothStatus status) {
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Template A")
                .description("Demo")
                .status(status)
                .isTemplate(true)
                .createdBy(admin)
                .build();
    }

    private Panorama panorama(Booth booth, String name, String imageKey) {
        return Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name(name)
                .imageUrl("https://res.cloudinary.com/demo/image/upload/" + imageKey)
                .imageKey(imageKey)
                .orderIndex(0)
                .isDefault(false)
                .build();
    }
}
