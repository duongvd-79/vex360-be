package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.hall.dtos.request.CreateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.request.UpdateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.response.ExhibitionHallResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.mapper.ExhibitionHallMapper;
import com.example.vex360.features.hall.repositories.ExhibitionHallRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitionHallServiceUnitTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    @Mock
    private ExhibitionHallRepository hallRepository;
    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;
    @Mock
    private CloudService cloudService;
    @Mock
    private PanoramaImageCleanupService assetCleanupService;

    private ExhibitionHallService hallService;
    private User organizer;
    private Exhibition exhibition;
    private UUID exhibitionUuid;

    @BeforeEach
    void setUp() {
        hallService = new ExhibitionHallService(
                hallRepository,
                exhibitionService,
                timelinePolicy,
                cloudService,
                assetCleanupService,
                new ExhibitionHallMapper());
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibitionUuid = UUID.randomUUID();
        exhibition = Exhibition.builder()
                .id(10)
                .uuid(exhibitionUuid)
                .organizer(organizer)
                .name("Expo")
                .startDate(TODAY.plusDays(10))
                .endDate(TODAY.plusDays(15))
                .estimatedBooths(10)
                .experienceMode(ExhibitionExperienceMode.WITH_BOOTHS)
                .status(ExhibitionStatus.REGISTRATION)
                .build();
        lenient().when(timelinePolicy.today()).thenReturn(TODAY);
        lenient().when(exhibitionService.findExhibitionForUpdate(exhibitionUuid))
                .thenReturn(exhibition);
        lenient().when(exhibitionService.findExhibitionEntityByUuid(exhibitionUuid))
                .thenReturn(exhibition);
    }

    @ParameterizedTest
    @EnumSource(ExhibitionExperienceMode.class)
    void ownerCreatesHallForBothExperienceModes(ExhibitionExperienceMode experienceMode) {
        exhibition.setExperienceMode(experienceMode);
        when(hallRepository.saveAndFlush(any(ExhibitionHall.class)))
                .thenAnswer(invocation -> {
                    ExhibitionHall hall = invocation.getArgument(0);
                    hall.setId(UUID.randomUUID());
                    return hall;
                });

        ExhibitionHallResponseDTO result = hallService.createHall(
                organizer,
                exhibitionUuid,
                new CreateExhibitionHallRequest(" Main Hall ", "Description"));

        ArgumentCaptor<ExhibitionHall> captor = ArgumentCaptor.forClass(ExhibitionHall.class);
        verify(hallRepository).saveAndFlush(captor.capture());
        assertSame(exhibition, captor.getValue().getExhibition());
        assertEquals("Main Hall", captor.getValue().getName());
        assertEquals(HallStatus.DRAFT, result.getStatus());
        assertEquals(ExhibitionStatus.REGISTRATION, exhibition.getStatus());
    }

    @Test
    void ownerReadsHall() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        when(hallRepository.findByExhibitionId(exhibition.getId())).thenReturn(Optional.of(hall));

        ExhibitionHallResponseDTO result = hallService.getHall(organizer, exhibitionUuid);

        assertEquals(hall.getId(), result.getId());
        assertEquals(exhibitionUuid, result.getExhibitionUuid());
    }

    @Test
    void ownerUpdatesDraftHall() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));
        when(hallRepository.save(hall)).thenReturn(hall);

        ExhibitionHallResponseDTO result = hallService.updateHall(
                organizer,
                exhibitionUuid,
                new UpdateExhibitionHallRequest(" Updated Hall ", "Updated"));

        assertEquals("Updated Hall", hall.getName());
        assertEquals("Updated", hall.getDescription());
        assertEquals(HallStatus.DRAFT, result.getStatus());
    }

    @Test
    void updatingRejectedHallReturnsItToDraft() {
        ExhibitionHall hall = hall(HallStatus.REJECTED);
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));
        when(hallRepository.save(hall)).thenReturn(hall);

        ExhibitionHallResponseDTO result = hallService.updateHall(
                organizer,
                exhibitionUuid,
                new UpdateExhibitionHallRequest("Corrected Hall", null));

        assertEquals(HallStatus.DRAFT, result.getStatus());
    }

    @Test
    void organizerReplacesHallBackgroundMusic() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        hall.setBackgroundMusicPublicId("old-music");
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", "music".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://cdn.example/ambient.mp3")
                .publicId("new-music")
                .fileSize(5L)
                .build();
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));
        when(cloudService.uploadToFolder(music, "hall-background-music")).thenReturn(upload);
        when(hallRepository.saveAndFlush(hall)).thenReturn(hall);

        ExhibitionHallResponseDTO result = hallService.updateBackgroundMusic(
                organizer, exhibitionUuid, music);

        assertEquals(upload.getUrl(), result.getBackgroundMusicUrl());
        assertEquals("ambient.mp3", result.getBackgroundMusicFileName());
        assertEquals(5L, result.getBackgroundMusicFileSize());
        verify(assetCleanupService).scheduleCleanup("old-music", "video");
    }

    @Test
    void organizerDeletesHallBackgroundMusic() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        hall.setBackgroundMusicUrl("https://cdn.example/ambient.mp3");
        hall.setBackgroundMusicPublicId("music-id");
        hall.setBackgroundMusicFileName("ambient.mp3");
        hall.setBackgroundMusicFileSize(5L);
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));
        when(hallRepository.saveAndFlush(hall)).thenReturn(hall);

        ExhibitionHallResponseDTO result = hallService.deleteBackgroundMusic(organizer, exhibitionUuid);

        assertNull(result.getBackgroundMusicUrl());
        verify(assetCleanupService).scheduleCleanup("music-id", "video");
    }

    @Test
    void invalidHallBackgroundMusicIsRejectedBeforeUpload() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.wav", "audio/mpeg", "music".getBytes());
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));

        AppException exception = assertThrows(AppException.class,
                () -> hallService.updateBackgroundMusic(organizer, exhibitionUuid, music));

        assertEquals(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), anyString());
    }

    @Test
    void failedHallSaveDeletesNewBackgroundMusicUpload() {
        ExhibitionHall hall = hall(HallStatus.DRAFT);
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", "music".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://cdn.example/ambient.mp3")
                .publicId("new-music")
                .fileSize(5L)
                .build();
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));
        when(cloudService.uploadToFolder(music, "hall-background-music")).thenReturn(upload);
        when(hallRepository.saveAndFlush(hall)).thenThrow(new RuntimeException("save failed"));

        assertThrows(RuntimeException.class,
                () -> hallService.updateBackgroundMusic(organizer, exhibitionUuid, music));

        verify(cloudService).delete("new-music", "video");
        verify(assetCleanupService, never()).scheduleCleanup(anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = HallStatus.class, names = { "PENDING_REVIEW", "PUBLISHED" })
    void pendingReviewAndPublishedHallsRejectUpdates(HallStatus status) {
        ExhibitionHall hall = hall(status);
        when(hallRepository.findByExhibitionIdForUpdate(exhibition.getId())).thenReturn(Optional.of(hall));

        AppException exception = assertThrows(AppException.class,
                () -> hallService.updateHall(
                        organizer,
                        exhibitionUuid,
                        new UpdateExhibitionHallRequest("Updated", null)));

        assertEquals(ErrorCode.HALL_CHANGES_NOT_ALLOWED, exception.getErrorCode());
        verify(hallRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ExhibitionStatus.class, names = { "PENDING", "REJECTED", "ACTIVE", "COMPLETED" })
    void nonEligibleExhibitionStatusesRejectHallCreation(ExhibitionStatus status) {
        exhibition.setStatus(status);

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        organizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.HALL_EXHIBITION_NOT_ELIGIBLE, exception.getErrorCode());
        verify(hallRepository, never()).saveAndFlush(any());
    }

    @Test
    void startedExhibitionRejectsHallCreation() {
        exhibition.setStartDate(TODAY);

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        organizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED, exception.getErrorCode());
    }

    @Test
    void startedExhibitionRejectsSceneMutationBeforeHallLookup() {
        exhibition.setStartDate(TODAY);

        AppException exception = assertThrows(
                AppException.class,
                () -> hallService.findEditableHallForUpdate(organizer, exhibitionUuid));

        assertEquals(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED, exception.getErrorCode());
        verify(hallRepository, never()).findByExhibitionIdForUpdate(any());
    }

    @Test
    void foreignOrganizerCannotCreateHall() {
        User foreignOrganizer = User.builder().id(UUID.randomUUID()).build();

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        foreignOrganizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void missingExhibitionIsRejected() {
        when(exhibitionService.findExhibitionForUpdate(exhibitionUuid))
                .thenThrow(new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        organizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void duplicateHallReturnsConflictBeforeInsert() {
        when(hallRepository.existsByExhibitionId(exhibition.getId())).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        organizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.HALL_ALREADY_EXISTS, exception.getErrorCode());
        verify(hallRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateConstraintReturnsSameConflict() {
        when(hallRepository.saveAndFlush(any(ExhibitionHall.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        AppException exception = assertThrows(AppException.class,
                () -> hallService.createHall(
                        organizer,
                        exhibitionUuid,
                        new CreateExhibitionHallRequest("Hall", null)));

        assertEquals(ErrorCode.HALL_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void missingHallIsRejected() {
        when(hallRepository.findByExhibitionId(exhibition.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> hallService.getHall(organizer, exhibitionUuid));

        assertEquals(ErrorCode.HALL_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void unauthenticatedOrganizerIsRejected() {
        AppException exception = assertThrows(
                AppException.class,
                () -> hallService.getHall(null, exhibitionUuid));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    private ExhibitionHall hall(HallStatus status) {
        return ExhibitionHall.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .name("Hall")
                .description("Description")
                .status(status)
                .createdAt(Instant.parse("2026-08-21T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-21T00:00:00Z"))
                .build();
    }
}
