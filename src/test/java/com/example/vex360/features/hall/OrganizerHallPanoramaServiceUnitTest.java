package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.hall.dtos.request.CreateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.request.UpdateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.response.HallPanoramaResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallPanoramaService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class OrganizerHallPanoramaServiceUnitTest {
    @Mock
    private HallPanoramaRepository panoramaRepository;
    @Mock
    private HallHotspotRepository hotspotRepository;
    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private CloudService cloudService;
    @Mock
    private PanoramaImageCleanupService imageCleanupService;

    private OrganizerHallPanoramaService service;
    private User organizer;
    private UUID exhibitionUuid;
    private ExhibitionHall hall;
    private MultipartFile image;

    @BeforeEach
    void setUp() {
        service = new OrganizerHallPanoramaService(
                panoramaRepository,
                hotspotRepository,
                hallService,
                cloudService,
                imageCleanupService,
                new HallSceneMapper());
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibitionUuid = UUID.randomUUID();
        hall = ExhibitionHall.builder()
                .id(UUID.randomUUID())
                .name("Hall")
                .status(HallStatus.DRAFT)
                .build();
        image = new MockMultipartFile(
                "image", "hall.jpg", "image/jpeg", "panorama".getBytes());
    }

    @Test
    void createsPanoramaWithNextOrderWithoutForcingDefault() {
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(cloudService.uploadToFolder(image, "panorama")).thenReturn(upload("new-key"));
        when(panoramaRepository.findMaxOrderIndexByHallId(hall.getId())).thenReturn(4);
        when(panoramaRepository.saveAndFlush(any(HallPanorama.class)))
                .thenAnswer(invocation -> {
                    HallPanorama panorama = invocation.getArgument(0);
                    panorama.setId(UUID.randomUUID());
                    return panorama;
                });

        HallPanoramaResponseDTO result = service.createPanorama(
                organizer,
                exhibitionUuid,
                new CreateHallPanoramaRequest(" Main ", null, false),
                image);

        ArgumentCaptor<HallPanorama> captor = ArgumentCaptor.forClass(HallPanorama.class);
        verify(panoramaRepository).saveAndFlush(captor.capture());
        assertEquals("Main", captor.getValue().getName());
        assertEquals(5, captor.getValue().getOrderIndex());
        assertFalse(captor.getValue().getIsDefault());
        assertEquals("new-key", result.getImageKey());
        verify(panoramaRepository, never()).clearDefaultForHall(any());
    }

    @Test
    void selectingDefaultClearsPreviousDefaultUnderHallLock() {
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(cloudService.uploadToFolder(image, "panorama")).thenReturn(upload("new-key"));
        when(panoramaRepository.saveAndFlush(any(HallPanorama.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createPanorama(
                organizer,
                exhibitionUuid,
                new CreateHallPanoramaRequest("Default", 0, true),
                image);

        InOrder order = inOrder(hallService, panoramaRepository);
        order.verify(hallService).findEditableHallForUpdate(organizer, exhibitionUuid);
        order.verify(panoramaRepository).clearDefaultForHall(hall.getId());
        order.verify(panoramaRepository).saveAndFlush(any(HallPanorama.class));
    }

    @Test
    void updatesOrderingAndReplacesImageAfterDatabaseSuccess() {
        HallPanorama panorama = panorama("old-key");
        MultipartFile replacement = new MockMultipartFile(
                "image", "replacement.jpg", "image/jpeg", "new".getBytes());
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallIdForUpdate(panorama.getId(), hall.getId()))
                .thenReturn(Optional.of(panorama));
        when(cloudService.uploadToFolder(replacement, "panorama")).thenReturn(upload("new-key"));
        when(panoramaRepository.saveAndFlush(panorama)).thenReturn(panorama);

        HallPanoramaResponseDTO result = service.updatePanorama(
                organizer,
                exhibitionUuid,
                panorama.getId(),
                new UpdateHallPanoramaRequest("Updated", 7, true),
                replacement);

        assertEquals("Updated", result.getName());
        assertEquals(7, result.getOrderIndex());
        assertEquals("new-key", result.getImageKey());
        InOrder order = inOrder(panoramaRepository, imageCleanupService);
        order.verify(panoramaRepository).saveAndFlush(panorama);
        order.verify(imageCleanupService).scheduleCleanup("old-key");
    }

    @Test
    void databaseFailureDeletesOnlyNewUpload() {
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(cloudService.uploadToFolder(image, "panorama")).thenReturn(upload("new-key"));
        when(panoramaRepository.findMaxOrderIndexByHallId(hall.getId())).thenReturn(-1);
        when(panoramaRepository.saveAndFlush(any(HallPanorama.class)))
                .thenThrow(new RuntimeException("database failed"));

        assertThrows(
                RuntimeException.class,
                () -> service.createPanorama(
                        organizer,
                        exhibitionUuid,
                        new CreateHallPanoramaRequest("Main", null, false),
                        image));

        verify(cloudService).delete("new-key", "image");
        verify(imageCleanupService, never()).scheduleCleanup(any(String.class));
    }

    @Test
    void uploadFailureDoesNotWriteDatabase() {
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(cloudService.uploadToFolder(image, "panorama"))
                .thenThrow(new AppException(ErrorCode.UPLOAD_FAILED));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createPanorama(
                        organizer,
                        exhibitionUuid,
                        new CreateHallPanoramaRequest("Main", null, false),
                        image));

        assertEquals(ErrorCode.UPLOAD_FAILED, exception.getErrorCode());
        verify(panoramaRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRemovesIncomingNavigationBeforePanoramaAndSchedulesCleanupLast() {
        HallPanorama panorama = panorama("old-key");
        HallHotspot incoming = HallHotspot.builder().id(UUID.randomUUID()).targetPanorama(panorama).build();
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallIdForUpdate(panorama.getId(), hall.getId()))
                .thenReturn(Optional.of(panorama));
        when(hotspotRepository.findAllByTargetPanoramaId(panorama.getId()))
                .thenReturn(List.of(incoming));

        HallPanoramaResponseDTO result = service.deletePanorama(
                organizer,
                exhibitionUuid,
                panorama.getId());

        assertEquals(panorama.getId(), result.getId());
        InOrder order = inOrder(hotspotRepository, panoramaRepository, imageCleanupService);
        order.verify(hotspotRepository).deleteAll(List.of(incoming));
        order.verify(hotspotRepository).flush();
        order.verify(panoramaRepository).delete(panorama);
        order.verify(panoramaRepository).flush();
        order.verify(imageCleanupService).scheduleCleanup("old-key");
    }

    @Test
    void sceneMutationStopsBeforeUploadWhenHallIsLockedOrExhibitionStarted() {
        AppException locked = new AppException(ErrorCode.HALL_CHANGES_NOT_ALLOWED);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenThrow(locked);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createPanorama(
                        organizer,
                        exhibitionUuid,
                        new CreateHallPanoramaRequest("Main", 0, false),
                        image));

        assertSame(locked, exception);
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void panoramaIdOutsideOwnedHallIsHiddenAsNotFound() {
        UUID panoramaId = UUID.randomUUID();
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(panoramaId, hall.getId()))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getPanorama(organizer, exhibitionUuid, panoramaId));

        assertEquals(ErrorCode.HALL_PANORAMA_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void foreignOrganizerStopsBeforeSceneIdLookup() {
        User foreignOrganizer = User.builder().id(UUID.randomUUID()).build();
        AppException unauthorized = new AppException(ErrorCode.UNAUTHORIZED);
        when(hallService.findOwnedHallEntity(foreignOrganizer, exhibitionUuid))
                .thenThrow(unauthorized);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getPanorama(
                        foreignOrganizer, exhibitionUuid, UUID.randomUUID()));

        assertSame(unauthorized, exception);
        verify(panoramaRepository, never()).findByIdAndHallId(any(), any());
    }

    private HallPanorama panorama(String imageKey) {
        return HallPanorama.builder()
                .id(UUID.randomUUID())
                .hall(hall)
                .name("Panorama")
                .imageUrl("https://cdn.example/panorama")
                .imageKey(imageKey)
                .fileSize(10L)
                .orderIndex(0)
                .isDefault(false)
                .build();
    }

    private CloudinaryResponse upload(String imageKey) {
        return CloudinaryResponse.builder()
                .url("https://cdn.example/" + imageKey)
                .publicId(imageKey)
                .fileSize(10L)
                .build();
    }
}
