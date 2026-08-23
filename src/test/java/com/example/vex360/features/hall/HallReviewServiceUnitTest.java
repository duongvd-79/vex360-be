package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.hall.dtos.request.RejectHallReviewRequest;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallPublishedRevision;
import com.example.vex360.features.hall.entities.HallReviewRequest;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.enums.HallReviewStatus;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.repositories.ExhibitionHallRepository;
import com.example.vex360.features.hall.repositories.HallPublishedRevisionRepository;
import com.example.vex360.features.hall.repositories.HallReviewRequestRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.HallReviewDiffService;
import com.example.vex360.features.hall.services.HallReviewService;
import com.example.vex360.features.hall.services.HallReviewSnapshot;
import com.example.vex360.features.hall.services.HallReviewSnapshotFactory;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class HallReviewServiceUnitTest {
    private static final Instant NOW = Instant.parse("2026-08-21T03:00:00Z");

    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private ExhibitionHallRepository hallRepository;
    @Mock
    private HallReviewRequestRepository reviewRequestRepository;
    @Mock
    private HallPublishedRevisionRepository publishedRevisionRepository;
    @Mock
    private HallReviewSnapshotFactory snapshotFactory;
    @Mock
    private MailService mailService;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    private HallReviewDiffService diffService;
    private HallReviewService service;
    private User organizer;
    private User admin;
    private Exhibition exhibition;
    private ExhibitionHall hall;

    @BeforeEach
    void setUp() {
        diffService = new HallReviewDiffService(JsonMapper.builder().build());
        service = new HallReviewService(
                hallService, hallRepository, reviewRequestRepository, publishedRevisionRepository,
                snapshotFactory, diffService, mailService, afterCommitExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));
        organizer = User.builder().id(UUID.randomUUID()).email("owner@example.com")
                .fullName("Owner").role(Role.ORGANIZER).build();
        admin = User.builder().id(UUID.randomUUID()).fullName("Admin").role(Role.ADMIN).build();
        exhibition = Exhibition.builder().id(1).uuid(UUID.randomUUID()).name("Expo")
                .organizer(organizer).startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 5)).estimatedBooths(10)
                .experienceMode(ExhibitionExperienceMode.WITH_BOOTHS)
                .status(ExhibitionStatus.REGISTRATION).build();
        hall = ExhibitionHall.builder().id(UUID.randomUUID()).exhibition(exhibition)
                .name("Main Hall").status(HallStatus.DRAFT).build();
    }

    @Test
    void validSubmissionPersistsVersionedImmutableSnapshotWithBoothSlot() {
        HallReviewSnapshot snapshot = validSnapshot("Main Hall");
        stubSubmission(snapshot, Optional.empty(), 0);

        var response = service.submit(organizer, exhibition.getUuid());

        ArgumentCaptor<HallReviewRequest> captor = ArgumentCaptor.forClass(HallReviewRequest.class);
        verify(reviewRequestRepository).saveAndFlush(captor.capture());
        HallReviewRequest saved = captor.getValue();
        assertEquals(1, response.getVersionNumber());
        assertEquals(HallReviewStatus.PENDING, response.getStatus());
        assertEquals(HallStatus.PENDING_REVIEW, hall.getStatus());
        assertTrue(saved.getContentSnapshotJson().contains("boothSlotIndex"));
        assertFalse(saved.getContentSnapshotJson().contains("targetBoothId"));
        assertEquals(ExhibitionStatus.REGISTRATION, exhibition.getStatus());
    }

    @Test
    void incompleteHallCannotSubmit() {
        HallReviewSnapshot invalid = validSnapshot("Main Hall");
        invalid.setPanoramas(List.of());
        when(hallService.findEditableHallForUpdate(organizer, exhibition.getUuid())).thenReturn(hall);
        when(snapshotFactory.create(hall)).thenReturn(invalid);

        AppException exception = assertThrows(AppException.class,
                () -> service.submit(organizer, exhibition.getUuid()));

        assertSame(ErrorCode.HALL_REVIEW_NOT_READY, exception.getErrorCode());
        assertEquals(HallStatus.DRAFT, hall.getStatus());
        verify(reviewRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidInfoReferenceCannotSubmit() {
        HallReviewSnapshot invalid = validSnapshot("Main Hall");
        UUID panoramaId = invalid.getPanoramas().getFirst().getId();
        invalid.setHotspots(List.of(HallReviewSnapshot.HotspotItem.builder()
                .id(UUID.randomUUID())
                .type(HallHotspotType.INFO)
                .infoContentType(HallInfoContentType.IMAGE)
                .name("Image info")
                .sourcePanoramaId(panoramaId)
                .mediaAssetId(UUID.randomUUID())
                .xPosition(1D).yPosition(2D).zPosition(3D)
                .build()));
        when(hallService.findEditableHallForUpdate(organizer, exhibition.getUuid())).thenReturn(hall);
        when(snapshotFactory.create(hall)).thenReturn(invalid);

        AppException exception = assertThrows(AppException.class,
                () -> service.submit(organizer, exhibition.getUuid()));

        assertSame(ErrorCode.HALL_REVIEW_NOT_READY, exception.getErrorCode());
        verify(reviewRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void oldSnapshotInfoContentTypesAreInferred() {
        UUID itemId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        HallReviewSnapshot oldSnapshot = HallReviewSnapshot.builder()
                .snapshotSchemaVersion(2)
                .hotspots(List.of(
                        HallReviewSnapshot.HotspotItem.builder()
                                .type(HallHotspotType.INFO).itemId(itemId).build(),
                        HallReviewSnapshot.HotspotItem.builder()
                                .type(HallHotspotType.INFO).mediaAssetId(videoId).build(),
                        HallReviewSnapshot.HotspotItem.builder()
                                .type(HallHotspotType.INFO).infoText("Legacy text").build(),
                        HallReviewSnapshot.HotspotItem.builder()
                                .type(HallHotspotType.INFO).build()))
                .mediaAssets(List.of(HallReviewSnapshot.MediaAssetItem.builder()
                        .id(videoId).type(MediaAssetType.VIDEO).build()))
                .build();

        HallReviewSnapshot normalized = diffService.readSnapshot(diffService.writeJson(oldSnapshot));

        assertEquals(
                List.of(
                        HallInfoContentType.ITEM,
                        HallInfoContentType.VIDEO,
                        HallInfoContentType.TEXT,
                        HallInfoContentType.NONE),
                normalized.getHotspots().stream()
                        .map(HallReviewSnapshot.HotspotItem::getInfoContentType)
                        .toList());
    }

    @Test
    void standaloneHallCannotSubmitBoothEntryPlaceholder() {
        HallReviewSnapshot invalid = validSnapshot("Main Hall");
        invalid.getHall().setExperienceMode(ExhibitionExperienceMode.STANDALONE);
        when(hallService.findEditableHallForUpdate(organizer, exhibition.getUuid())).thenReturn(hall);
        when(snapshotFactory.create(hall)).thenReturn(invalid);

        AppException exception = assertThrows(AppException.class,
                () -> service.submit(organizer, exhibition.getUuid()));

        assertSame(ErrorCode.HALL_REVIEW_NOT_READY, exception.getErrorCode());
        verify(reviewRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvedHallCannotBeSubmittedAgain() {
        when(hallService.findEditableHallForUpdate(organizer, exhibition.getUuid()))
                .thenThrow(new AppException(ErrorCode.HALL_CHANGES_NOT_ALLOWED));

        AppException exception = assertThrows(AppException.class,
                () -> service.submit(organizer, exhibition.getUuid()));

        assertSame(ErrorCode.HALL_CHANGES_NOT_ALLOWED, exception.getErrorCode());
        verify(snapshotFactory, never()).create(any());
    }

    @Test
    void rejectedHallResubmissionIncrementsVersionAndComparesLatestRejectedSnapshot() {
        HallReviewSnapshot previousSnapshot = validSnapshot("Old Hall");
        HallReviewRequest previous = request(HallReviewStatus.REJECTED, 1, previousSnapshot);
        HallReviewSnapshot current = validSnapshot("Corrected Hall");
        stubSubmission(current, Optional.of(previous), 1);

        var response = service.submit(organizer, exhibition.getUuid());

        assertEquals(2, response.getVersionNumber());
        assertEquals(previous.getId(), response.getChangeSummary().getComparedToRequestId());
        assertTrue(response.getChangeSummary().getChangedSections().contains("HALL"));
    }

    @Test
    void approvalPublishesExactReviewedSnapshotAndDoesNotChangeExhibitionStatus() {
        HallReviewSnapshot snapshot = validSnapshot("Reviewed Hall");
        HallReviewRequest request = request(HallReviewStatus.PENDING, 2, snapshot);
        hall.setStatus(HallStatus.PENDING_REVIEW);
        when(reviewRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(publishedRevisionRepository.findByHallId(hall.getId())).thenReturn(Optional.empty());
        when(reviewRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(admin, request.getId());

        ArgumentCaptor<HallPublishedRevision> captor = ArgumentCaptor.forClass(HallPublishedRevision.class);
        verify(publishedRevisionRepository).save(captor.capture());
        assertEquals(request.getContentSnapshotJson(), captor.getValue().getContentSnapshotJson());
        assertEquals(2, captor.getValue().getVersionNumber());
        assertEquals(HallReviewStatus.APPROVED, response.getStatus());
        assertEquals(HallStatus.PUBLISHED, hall.getStatus());
        assertEquals(ExhibitionStatus.REGISTRATION, exhibition.getStatus());
    }

    @Test
    void adminCannotApproveHallOnExhibitionStartDate() {
        HallReviewRequest request = request(HallReviewStatus.PENDING, 1, validSnapshot("Main Hall"));
        hall.setStatus(HallStatus.PENDING_REVIEW);
        exhibition.setStartDate(LocalDate.of(2026, 8, 21));
        when(reviewRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(AppException.class,
                () -> service.approve(admin, request.getId()));

        assertSame(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED, exception.getErrorCode());
        assertEquals(HallReviewStatus.PENDING, request.getStatus());
        assertEquals(HallStatus.PENDING_REVIEW, hall.getStatus());
        verify(publishedRevisionRepository, never()).save(any());
        verify(reviewRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectionRecordsReasonAndUnlocksCorrectionState() {
        HallReviewRequest request = request(HallReviewStatus.PENDING, 1, validSnapshot("Main Hall"));
        hall.setStatus(HallStatus.PENDING_REVIEW);
        when(reviewRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(reviewRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.reject(admin, request.getId(), new RejectHallReviewRequest("  Missing media  "));

        assertEquals(HallReviewStatus.REJECTED, response.getStatus());
        assertEquals("Missing media", response.getRejectedReason());
        assertEquals(HallStatus.REJECTED, hall.getStatus());
        verify(publishedRevisionRepository, never()).save(any());
    }

    @Test
    void approvedDecisionIsKeptWhenNotificationSchedulingFails() {
        HallReviewRequest request = request(HallReviewStatus.PENDING, 1, validSnapshot("Main Hall"));
        hall.setStatus(HallStatus.PENDING_REVIEW);
        when(reviewRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(publishedRevisionRepository.findByHallId(hall.getId())).thenReturn(Optional.empty());
        when(reviewRequestRepository.saveAndFlush(request)).thenReturn(request);
        doThrow(new IllegalStateException("mail unavailable")).when(afterCommitExecutor).execute(any());

        var response = service.approve(admin, request.getId());

        assertEquals(HallReviewStatus.APPROVED, response.getStatus());
        assertEquals(HallStatus.PUBLISHED, hall.getStatus());
        verify(publishedRevisionRepository).save(any());
    }

    @Test
    void nonAdminCannotDecideRequest() {
        AppException exception = assertThrows(AppException.class,
                () -> service.approve(organizer, UUID.randomUUID()));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(reviewRequestRepository, never()).findByIdForUpdate(any());
    }

    private void stubSubmission(
            HallReviewSnapshot snapshot,
            Optional<HallReviewRequest> previous,
            long existingCount) {
        when(hallService.findEditableHallForUpdate(organizer, exhibition.getUuid())).thenReturn(hall);
        when(snapshotFactory.create(hall)).thenReturn(snapshot);
        when(reviewRequestRepository.findTopByHallIdOrderByVersionNumberDescSubmittedAtDesc(hall.getId()))
                .thenReturn(previous);
        when(reviewRequestRepository.countByHallId(hall.getId())).thenReturn(existingCount);
        when(reviewRequestRepository.saveAndFlush(any(HallReviewRequest.class))).thenAnswer(invocation -> {
            HallReviewRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            request.setSubmittedAt(NOW);
            return request;
        });
    }

    private HallReviewRequest request(
            HallReviewStatus status,
            int version,
            HallReviewSnapshot snapshot) {
        var summary = diffService.buildSummary(snapshot, null, version);
        return HallReviewRequest.builder().id(UUID.randomUUID()).hall(hall).status(status)
                .submittedBy(organizer).submittedAt(NOW.minusSeconds(60)).versionNumber(version)
                .contentSnapshotJson(diffService.writeJson(snapshot))
                .changeSummaryJson(diffService.writeJson(summary)).build();
    }

    private HallReviewSnapshot validSnapshot(String hallName) {
        UUID panoramaId = UUID.randomUUID();
        return HallReviewSnapshot.builder()
                .snapshotSchemaVersion(1)
                .hall(HallReviewSnapshot.HallItem.builder().id(hall.getId())
                        .exhibitionUuid(exhibition.getUuid()).exhibitionName(exhibition.getName())
                        .experienceMode(exhibition.getExperienceMode()).name(hallName).build())
                .panoramas(List.of(HallReviewSnapshot.PanoramaItem.builder().id(panoramaId)
                        .name("Entrance").imageUrl("pano.jpg").imageKey("pano-key")
                        .fileSize(100L).orderIndex(0).isDefault(true).build()))
                .hotspots(List.of(HallReviewSnapshot.HotspotItem.builder().id(UUID.randomUUID())
                        .type(HallHotspotType.BOOTH_ENTRY).name("Booth slot 1")
                        .sourcePanoramaId(panoramaId).boothSlotIndex(0)
                        .xPosition(1.0).yPosition(2.0).zPosition(3.0).build()))
                .items(List.of()).mediaAssets(List.of()).build();
    }
}
