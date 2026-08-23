package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO;
import com.example.vex360.features.hall.entities.HallPublishedRevision;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.repositories.HallPublishedRevisionRepository;
import com.example.vex360.features.hall.services.HallReviewDiffService;
import com.example.vex360.features.hall.services.HallReviewSnapshot;
import com.example.vex360.features.hall.services.HallVisitorService;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class HallVisitorServiceUnitTest {
    private static final String APPROVED_SNAPSHOT = "approved-snapshot";

    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private HallPublishedRevisionRepository publishedRevisionRepository;
    @Mock
    private HallReviewDiffService reviewDiffService;
    @Mock
    private VisitorBoothService visitorBoothService;

    private HallVisitorService service;
    private UUID exhibitionUuid;
    private ExhibitionResponseDTO exhibition;
    private HallReviewSnapshot snapshot;

    @BeforeEach
    void setUp() {
        ExhibitionTimelinePolicy timelinePolicy = new ExhibitionTimelinePolicy(
                Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));
        service = new HallVisitorService(
                exhibitionService,
                timelinePolicy,
                publishedRevisionRepository,
                reviewDiffService,
                visitorBoothService);
        exhibitionUuid = UUID.randomUUID();
        exhibition = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .name("Expo")
                .description("Description")
                .category("Technology")
                .startDate(LocalDate.of(2026, 1, 10))
                .endDate(LocalDate.of(2026, 1, 12))
                .status(ExhibitionStatus.ACTIVE.name())
                .experienceMode(ExhibitionExperienceMode.STANDALONE)
                .build();
        snapshot = snapshot();
    }

    @Test
    void standaloneReturnsApprovedHallWithoutBoothData() {
        mockApprovedSnapshot();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        PublicExhibitionExperienceResponseDTO result = service.getExperience(exhibitionUuid);

        assertFalse(result.usesDefaultHall());
        assertNull(result.booths());
        assertEquals("Approved Hall", result.hall().name());
        assertEquals("https://cdn.example/hall.mp3", result.hall().backgroundMusicUrl());
        assertEquals("hall.mp3", result.hall().backgroundMusicFileName());
        assertEquals(100L, result.hall().backgroundMusicFileSize());
        assertEquals(1, result.hall().items().size());
        assertEquals(1, result.hall().panoramas().get(0).hotspots().size());
        assertEquals(HallHotspotType.INFO,
                result.hall().panoramas().get(0).hotspots().get(0).type());
        assertEquals(HallInfoContentType.TEXT,
                result.hall().panoramas().get(0).hotspots().get(0).infoContentType());
        verify(reviewDiffService).readSnapshot(APPROVED_SNAPSHOT);
        verify(visitorBoothService, never()).findPublishedBoothsByFirstApproval(exhibitionUuid);
    }

    @Test
    void withBoothsResolvesOnlyCurrentlyVisibleSlots() {
        exhibition.setExperienceMode(ExhibitionExperienceMode.WITH_BOOTHS);
        Booth visibleBooth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Visible Booth")
                .thumbnailUrl("booth.jpg")
                .build();
        mockApprovedSnapshot();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid))
                .thenReturn(List.of(visibleBooth));

        PublicExhibitionExperienceResponseDTO result = service.getExperience(exhibitionUuid);

        assertFalse(result.usesDefaultHall());
        assertEquals(1, result.booths().size());
        assertEquals(visibleBooth.getId(), result.booths().get(0).id());
        assertEquals(3, result.hall().panoramas().get(0).hotspots().size());
        assertEquals(visibleBooth.getId(),
                result.hall().panoramas().get(0).hotspots().get(1).targetBooth().id());
        assertNull(result.hall().panoramas().get(0).hotspots().get(2).targetBooth());
        verify(visitorBoothService).findPublishedBoothsByFirstApproval(exhibitionUuid);
    }

    @Test
    void publicExperienceKeepsInfoMediaAndHallItemReferences() {
        UUID panoramaId = snapshot.getPanoramas().getFirst().getId();
        UUID mediaId = snapshot.getMediaAssets().getFirst().getId();
        UUID itemId = snapshot.getItems().getFirst().getId();
        snapshot.setHotspots(List.of(
                HallReviewSnapshot.HotspotItem.builder()
                        .id(UUID.randomUUID())
                        .type(HallHotspotType.INFO)
                        .infoContentType(HallInfoContentType.IMAGE)
                        .name("Image")
                        .sourcePanoramaId(panoramaId)
                        .mediaAssetId(mediaId)
                        .build(),
                HallReviewSnapshot.HotspotItem.builder()
                        .id(UUID.randomUUID())
                        .type(HallHotspotType.INFO)
                        .infoContentType(HallInfoContentType.ITEM)
                        .name("Item")
                        .sourcePanoramaId(panoramaId)
                        .itemId(itemId)
                        .build()));
        mockApprovedSnapshot();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        PublicExhibitionExperienceResponseDTO result = service.getExperience(exhibitionUuid);

        var hotspots = result.hall().panoramas().getFirst().hotspots();
        assertEquals(mediaId, hotspots.get(0).mediaAsset().id());
        assertEquals(itemId, hotspots.get(1).item().id());
        assertEquals(HallInfoContentType.IMAGE, hotspots.get(0).infoContentType());
        assertEquals(HallInfoContentType.ITEM, hotspots.get(1).infoContentType());
    }

    @Test
    void activeExhibitionWithoutApprovedHallUsesFrontendDefaultHall() {
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(publishedRevisionRepository.findByHallExhibitionUuid(exhibitionUuid))
                .thenReturn(Optional.empty());

        PublicExhibitionExperienceResponseDTO result = service.getExperience(exhibitionUuid);

        assertTrue(result.usesDefaultHall());
        assertNull(result.hall());
        assertNull(result.booths());
        verify(reviewDiffService, never()).readSnapshot(APPROVED_SNAPSHOT);
        verify(visitorBoothService, never()).findPublishedBoothsByFirstApproval(exhibitionUuid);
    }

    @Test
    void publishedExhibitionCanShowInfoButCannotEnterExperience() {
        exhibition.setStatus(ExhibitionStatus.PUBLISHED.name());
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getExperience(exhibitionUuid));

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, exception.getErrorCode());
        verify(publishedRevisionRepository, never()).findByHallExhibitionUuid(exhibitionUuid);
    }

    private void mockApprovedSnapshot() {
        HallPublishedRevision revision = HallPublishedRevision.builder()
                .contentSnapshotJson(APPROVED_SNAPSHOT)
                .build();
        when(publishedRevisionRepository.findByHallExhibitionUuid(exhibitionUuid))
                .thenReturn(Optional.of(revision));
        when(reviewDiffService.readSnapshot(APPROVED_SNAPSHOT)).thenReturn(snapshot);
    }

    private HallReviewSnapshot snapshot() {
        UUID hallId = UUID.randomUUID();
        UUID panoramaId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        return HallReviewSnapshot.builder()
                .snapshotSchemaVersion(1)
                .hall(HallReviewSnapshot.HallItem.builder()
                        .id(hallId)
                        .exhibitionUuid(exhibitionUuid)
                        .exhibitionName("Expo")
                        .experienceMode(ExhibitionExperienceMode.WITH_BOOTHS)
                        .name("Approved Hall")
                        .description("Immutable content")
                        .backgroundMusicUrl("https://cdn.example/hall.mp3")
                        .backgroundMusicFileName("hall.mp3")
                        .backgroundMusicFileSize(100L)
                        .build())
                .panoramas(List.of(HallReviewSnapshot.PanoramaItem.builder()
                        .id(panoramaId)
                        .name("Main")
                        .imageUrl("hall.jpg")
                        .orderIndex(0)
                        .isDefault(true)
                        .build()))
                .hotspots(List.of(
                        HallReviewSnapshot.HotspotItem.builder()
                                .id(UUID.randomUUID())
                                .type(HallHotspotType.INFO)
                                .name("Info")
                                 .sourcePanoramaId(panoramaId)
                                 .infoText("Welcome")
                                 .infoContentType(HallInfoContentType.TEXT)
                                 .build(),
                        HallReviewSnapshot.HotspotItem.builder()
                                .id(UUID.randomUUID())
                                .type(HallHotspotType.BOOTH_ENTRY)
                                .name("Booth slot 0")
                                .sourcePanoramaId(panoramaId)
                                .boothSlotIndex(0)
                                .build(),
                        HallReviewSnapshot.HotspotItem.builder()
                                .id(UUID.randomUUID())
                                .type(HallHotspotType.BOOTH_ENTRY)
                                .name("Booth slot 1")
                                .sourcePanoramaId(panoramaId)
                                .boothSlotIndex(1)
                                .build()))
                .items(List.of(HallReviewSnapshot.ContentItem.builder()
                        .id(itemId)
                        .name("Artwork")
                        .description("Approved item")
                        .mediaAssetId(mediaId)
                        .displayOrder(0)
                        .build()))
                .mediaAssets(List.of(HallReviewSnapshot.MediaAssetItem.builder()
                        .id(mediaId)
                        .name("Artwork image")
                        .type(MediaAssetType.IMAGE)
                        .url("artwork.jpg")
                        .mimeType("image/jpeg")
                        .fileSize(100L)
                        .build()))
                .build();
    }
}
