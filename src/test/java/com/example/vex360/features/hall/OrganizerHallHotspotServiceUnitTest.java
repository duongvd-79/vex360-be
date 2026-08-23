package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.dtos.request.UpsertHallHotspotRequest;
import com.example.vex360.features.hall.dtos.response.HallHotspotResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallHotspotService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;

@ExtendWith(MockitoExtension.class)
class OrganizerHallHotspotServiceUnitTest {
    @Mock
    private HallPanoramaRepository panoramaRepository;
    @Mock
    private HallHotspotRepository hotspotRepository;
    @Mock
    private HallItemRepository itemRepository;
    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private ExhibitorMediaAssetService mediaAssetService;
    @Mock
    private VisitorBoothService visitorBoothService;

    private OrganizerHallHotspotService service;
    private User organizer;
    private UUID exhibitionUuid;
    private ExhibitionHall hall;
    private HallPanorama source;
    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        service = new OrganizerHallHotspotService(
                panoramaRepository,
                hotspotRepository,
                itemRepository,
                hallService,
                mediaAssetService,
                visitorBoothService,
                new HallSceneMapper());
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibitionUuid = UUID.randomUUID();
        exhibition = Exhibition.builder()
                .uuid(exhibitionUuid)
                .experienceMode(ExhibitionExperienceMode.WITH_BOOTHS)
                .build();
        hall = ExhibitionHall.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .name("Hall")
                .status(HallStatus.DRAFT)
                .build();
        source = panorama("Source");
    }

    @Test
    void createsNavigationOnlyToAnotherPanoramaInSameHall() {
        HallPanorama target = panorama("Target");
        UpsertHallHotspotRequest request = request(HallHotspotType.NAV);
        request.setTargetPanoramaId(target.getId());
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
        when(panoramaRepository.findByIdAndHallId(target.getId(), hall.getId()))
                .thenReturn(Optional.of(target));
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> {
                    HallHotspot hotspot = invocation.getArgument(0);
                    hotspot.setId(UUID.randomUUID());
                    return hotspot;
                });

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(HallHotspotType.NAV, result.getType());
        assertEquals(target.getId(), result.getTargetPanoramaId());
        assertEquals("Target", result.getName());
        assertNull(result.getMediaAsset());
    }

    @Test
    void rejectsCrossHallNavigationTarget() {
        UUID foreignTargetId = UUID.randomUUID();
        UpsertHallHotspotRequest request = request(HallHotspotType.NAV);
        request.setTargetPanoramaId(foreignTargetId);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
        when(panoramaRepository.findByIdAndHallId(foreignTargetId, hall.getId()))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_PANORAMA_NOT_FOUND, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void rejectsNavigationToSourcePanorama() {
        UpsertHallHotspotRequest request = request(HallHotspotType.NAV);
        request.setTargetPanoramaId(source.getId());
        stubEditableSource();

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_HOTSPOT_REQUEST_INVALID, exception.getErrorCode());
    }

    @Test
    void createsInfoHotspotWithTrimmedTextAndNoForeignReferences() {
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setName(" Information ");
        request.setInfoText(" Details ");
        request.setTargetPanoramaId(UUID.randomUUID());
        stubEditableSource();
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals("Information", result.getName());
        assertEquals("Details", result.getInfoText());
        assertEquals(HallInfoContentType.TEXT, result.getInfoContentType());
        assertNull(result.getTargetPanoramaId());
        assertNull(result.getMediaAsset());
    }

    @ParameterizedTest
    @EnumSource(value = HallInfoContentType.class, names = {"NONE", "IMAGE", "VIDEO", "ITEM"})
    void createsExplicitInfoContentWithoutBoothReferences(HallInfoContentType contentType) {
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setInfoContentType(contentType);
        request.setCorners(new HallHotspotCornersDTO(
                List.of(1D, 2D, 3D),
                List.of(4D, 5D, 6D),
                List.of(7D, 8D, 9D),
                List.of(10D, 11D, 12D)));
        switch (contentType) {
            case IMAGE, VIDEO -> {
                MediaAsset asset = MediaAsset.builder()
                        .id(UUID.randomUUID())
                        .name(contentType.name())
                        .type(contentType == HallInfoContentType.VIDEO
                                ? MediaAssetType.VIDEO
                                : MediaAssetType.IMAGE)
                        .build();
                request.setMediaAssetId(asset.getId());
                when(mediaAssetService.getMediaAssetForCurrentUser(organizer, asset.getId()))
                        .thenReturn(asset);
            }
            case ITEM -> {
                HallItem item = HallItem.builder().id(UUID.randomUUID()).hall(hall).name("Artifact").build();
                request.setItemId(item.getId());
                when(itemRepository.findByIdAndHallId(item.getId(), hall.getId()))
                        .thenReturn(Optional.of(item));
            }
            case NONE -> {
            }
            default -> throw new IllegalStateException();
        }
        stubEditableSource();
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(contentType, result.getInfoContentType());
        assertEquals(contentType == HallInfoContentType.ITEM, result.getItem() != null);
        assertEquals(contentType == HallInfoContentType.IMAGE || contentType == HallInfoContentType.VIDEO,
                result.getMediaAsset() != null);
        assertNull(result.getCorners());
        assertNull(result.getTargetBooth());
    }

    @Test
    void rejectsInfoMediaWithWrongType() {
        MediaAsset video = MediaAsset.builder()
                .id(UUID.randomUUID()).name("Video").type(MediaAssetType.VIDEO).build();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setInfoContentType(HallInfoContentType.IMAGE);
        request.setMediaAssetId(video.getId());
        stubEditableSource();
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, video.getId())).thenReturn(video);

        AppException exception = assertThrows(AppException.class,
                () -> service.createHotspot(organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_INFO_CONTENT_INVALID, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void rejectsForeignOrganizerMediaForInfo() {
        UUID assetId = UUID.randomUUID();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setInfoContentType(HallInfoContentType.VIDEO);
        request.setMediaAssetId(assetId);
        stubEditableSource();
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, assetId))
                .thenThrow(new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        AppException exception = assertThrows(AppException.class,
                () -> service.createHotspot(organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.MEDIA_ASSET_NOT_FOUND, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void rejectsBlankExplicitInfoText() {
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setInfoContentType(HallInfoContentType.TEXT);
        request.setInfoText("  ");
        stubEditableSource();

        AppException exception = assertThrows(AppException.class,
                () -> service.createHotspot(organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_INFO_CONTENT_INVALID, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void infersVideoInfoFromOwnedMediaWhenTypeIsMissing() {
        MediaAsset video = MediaAsset.builder()
                .id(UUID.randomUUID()).name("Video").type(MediaAssetType.VIDEO).build();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setMediaAssetId(video.getId());
        stubEditableSource();
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, video.getId())).thenReturn(video);
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(HallInfoContentType.VIDEO, result.getInfoContentType());
        assertEquals(video.getId(), result.getMediaAsset().getId());
    }

    @Test
    void createsMediaHotspotForOrganizerCompanyAndMapsCorners() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        MediaAsset asset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Video")
                .type(MediaAssetType.VIDEO)
                .url("https://cdn.example/video")
                .publicId("video-key")
                .mimeType("video/mp4")
                .fileSize(100L)
                .build();
        UpsertHallHotspotRequest request = request(HallHotspotType.MEDIA);
        request.setMediaAssetId(asset.getId());
        request.setCorners(new HallHotspotCornersDTO(
                List.of(1D, 2D, 3D),
                List.of(4D, 5D, 6D),
                List.of(7D, 8D, 9D),
                List.of(10D, 11D, 12D)));
        stubEditableSource();
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, asset.getId()))
                .thenReturn(asset);
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(asset.getId(), result.getMediaAsset().getId());
        assertEquals(HotspotMediaClickAction.DEFAULT, result.getMediaClickAction());
        assertEquals(List.of(10D, 11D, 12D), result.getCorners().getBr());
    }

    @Test
    void rejectsForeignCompanyMediaBeforeSaving() {
        UUID assetId = UUID.randomUUID();
        UpsertHallHotspotRequest request = request(HallHotspotType.MEDIA);
        request.setMediaAssetId(assetId);
        stubEditableSource();
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, assetId))
                .thenThrow(new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.MEDIA_ASSET_NOT_FOUND, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ExhibitionExperienceMode.class)
    void createsItemHotspotInBothExperienceModes(ExhibitionExperienceMode mode) {
        exhibition.setExperienceMode(mode);
        HallItem item = HallItem.builder()
                .id(UUID.randomUUID())
                .hall(hall)
                .name("Artwork")
                .build();
        UpsertHallHotspotRequest request = request(HallHotspotType.ITEM);
        request.setItemId(item.getId());
        request.setCorners(new HallHotspotCornersDTO(
                List.of(1D, 2D, 3D),
                List.of(4D, 5D, 6D),
                List.of(7D, 8D, 9D),
                List.of(10D, 11D, 12D)));
        stubEditableSource();
        when(itemRepository.findByIdAndHallId(item.getId(), hall.getId()))
                .thenReturn(Optional.of(item));
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(item.getId(), result.getItem().id());
        assertEquals("Artwork", result.getName());
        assertNull(result.getTargetBooth());
        assertEquals(List.of(10D, 11D, 12D), result.getCorners().getBr());
    }

    @Test
    void rejectsItemFromAnotherHall() {
        UUID itemId = UUID.randomUUID();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setItemId(itemId);
        stubEditableSource();
        when(itemRepository.findByIdAndHallId(itemId, hall.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_ITEM_NOT_FOUND, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void createsBoothPlaceholderAndResolvesPublishedBoothBySlot() {
        Booth booth = publishedBooth();
        MediaAsset placeholder = MediaAsset.builder()
                .id(UUID.randomUUID())
                .name("Booth placeholder")
                .type(MediaAssetType.IMAGE)
                .url("https://cdn.example/booth-placeholder")
                .build();

        UpsertHallHotspotRequest request = request(HallHotspotType.BOOTH_ENTRY);
        request.setBoothSlotIndex(0);
        stubEditableSource();
        request.setMediaAssetId(placeholder.getId());
        request.setCorners(new HallHotspotCornersDTO(
                List.of(1D, 2D, 3D),
                List.of(4D, 5D, 6D),
                List.of(7D, 8D, 9D),
                List.of(10D, 11D, 12D)));
        when(visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid))
                .thenReturn(List.of(booth));
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, placeholder.getId()))
                .thenReturn(placeholder);
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertNotNull(result.getTargetBooth());
        assertEquals(0, result.getBoothSlotIndex());
        assertEquals(booth.getId(), result.getTargetBooth().id());
        assertEquals("Booth slot 1", result.getName());
        assertEquals(placeholder.getId(), result.getMediaAsset().getId());
        assertEquals(List.of(10D, 11D, 12D), result.getCorners().getBr());
    }

    @Test
    void standaloneExhibitionRejectsBoothEntryBeforeBoothLookup() {
        exhibition.setExperienceMode(ExhibitionExperienceMode.STANDALONE);
        UpsertHallHotspotRequest request = request(HallHotspotType.BOOTH_ENTRY);
        request.setBoothSlotIndex(0);
        stubEditableSource();

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_BOOTH_ENTRY_NOT_ALLOWED, exception.getErrorCode());
        verify(visitorBoothService, never())
                .findPublishedBoothsByFirstApproval(any());
    }

    @Test
    void createsEmptyPlaceholderWhenNoBoothIsPublished() {
        UpsertHallHotspotRequest request = request(HallHotspotType.BOOTH_ENTRY);
        request.setBoothSlotIndex(0);
        stubEditableSource();
        when(visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid))
                .thenReturn(List.of());
        when(hotspotRepository.save(any(HallHotspot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HallHotspotResponseDTO result = service.createHotspot(
                organizer, exhibitionUuid, source.getId(), request);

        assertEquals(0, result.getBoothSlotIndex());
        assertNull(result.getTargetBooth());
    }

    @Test
    void rejectsDuplicatedBoothSlotAcrossHall() {
        UpsertHallHotspotRequest request = request(HallHotspotType.BOOTH_ENTRY);
        request.setBoothSlotIndex(2);
        stubEditableSource();
        when(hotspotRepository.existsBoothSlot(
                hall.getId(), HallHotspotType.BOOTH_ENTRY, 2, null)).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_BOOTH_SLOT_DUPLICATED, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = -1)
    void rejectsMissingOrNegativeBoothSlot(Integer slotIndex) {
        UpsertHallHotspotRequest request = request(HallHotspotType.BOOTH_ENTRY);
        request.setBoothSlotIndex(slotIndex);
        stubEditableSource();

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer, exhibitionUuid, source.getId(), request));

        assertEquals(ErrorCode.HALL_BOOTH_SLOT_INVALID, exception.getErrorCode());
        verify(hotspotRepository, never()).save(any());
    }

    @Test
    void changingTypeClearsAllStaleTargets() {
        HallHotspot hotspot = HallHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(source)
                .type(HallHotspotType.BOOTH_ENTRY)
                .name("Old")
                .targetPanorama(panorama("Old target"))
                .mediaAsset(MediaAsset.builder().id(UUID.randomUUID()).build())
                .item(HallItem.builder().id(UUID.randomUUID()).hall(hall).name("Old item").build())
                .boothSlotIndex(3)
                .infoText("Old info")
                .infoContentType(HallInfoContentType.TEXT)
                .cornerTlX(1D)
                .cornerTlY(1D)
                .cornerTlZ(1D)
                .build();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setName("Updated");
        stubEditableSource();
        when(hotspotRepository.findByIdAndSourcePanoramaIdForUpdate(
                hotspot.getId(), source.getId())).thenReturn(Optional.of(hotspot));
        when(hotspotRepository.save(hotspot)).thenReturn(hotspot);

        service.updateHotspot(
                organizer, exhibitionUuid, source.getId(), hotspot.getId(), request);

        assertNull(hotspot.getTargetPanorama());
        assertNull(hotspot.getMediaAsset());
        assertNull(hotspot.getItem());
        assertNull(hotspot.getBoothSlotIndex());
        assertEquals(HallInfoContentType.NONE, hotspot.getInfoContentType());
        assertNull(hotspot.getInfoText());
        assertNull(hotspot.getCornerTlX());
    }

    @Test
    void suppressesSlotWhenNotEnoughBoothsRemainVisible() {
        HallHotspot hotspot = HallHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(source)
                .type(HallHotspotType.BOOTH_ENTRY)
                .name("Booth")
                .boothSlotIndex(1)
                .xPosition(1D)
                .yPosition(2D)
                .zPosition(3D)
                .build();
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspot.getId(), source.getId()))
                .thenReturn(Optional.of(hotspot));
        when(visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid))
                .thenReturn(List.of(publishedBooth()));

        HallHotspotResponseDTO result = service.getHotspot(
                organizer, exhibitionUuid, source.getId(), hotspot.getId());

        assertNull(result.getTargetBooth());
    }

    @Test
    void compactsRemainingBoothsWhenEarlierBoothBecomesUnavailable() {
        Booth first = publishedBooth();
        Booth second = publishedBooth();
        HallHotspot slotZero = boothSlot(0);
        HallHotspot slotOne = boothSlot(1);
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
        when(hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(source.getId()))
                .thenReturn(List.of(slotZero, slotOne));
        when(visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid))
                .thenReturn(List.of(first, second), List.of(second));

        List<HallHotspotResponseDTO> before = service.getHotspots(
                organizer, exhibitionUuid, source.getId());
        List<HallHotspotResponseDTO> after = service.getHotspots(
                organizer, exhibitionUuid, source.getId());

        assertEquals(first.getId(), before.get(0).getTargetBooth().id());
        assertEquals(second.getId(), before.get(1).getTargetBooth().id());
        assertEquals(second.getId(), after.get(0).getTargetBooth().id());
        assertNull(after.get(1).getTargetBooth());
    }

    @Test
    void updatesAndDeletesOnlyHotspotUnderSourcePath() {
        HallHotspot hotspot = HallHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(source)
                .type(HallHotspotType.INFO)
                .name("Old")
                .xPosition(0D)
                .yPosition(0D)
                .zPosition(0D)
                .build();
        UpsertHallHotspotRequest request = request(HallHotspotType.INFO);
        request.setName("Updated");
        stubEditableSource();
        when(hotspotRepository.findByIdAndSourcePanoramaIdForUpdate(
                hotspot.getId(), source.getId())).thenReturn(Optional.of(hotspot));
        when(hotspotRepository.save(hotspot)).thenReturn(hotspot);

        HallHotspotResponseDTO updated = service.updateHotspot(
                organizer, exhibitionUuid, source.getId(), hotspot.getId(), request);
        HallHotspotResponseDTO deleted = service.deleteHotspot(
                organizer, exhibitionUuid, source.getId(), hotspot.getId());

        assertEquals("Updated", updated.getName());
        assertEquals(hotspot.getId(), deleted.getId());
        verify(hotspotRepository).delete(hotspot);
    }

    @Test
    void pendingReviewOrStartedExhibitionStopsBeforeSceneIdLookup() {
        AppException locked = new AppException(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenThrow(locked);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(
                        organizer,
                        exhibitionUuid,
                        source.getId(),
                        request(HallHotspotType.INFO)));

        assertSame(locked, exception);
        verify(panoramaRepository, never()).findByIdAndHallId(any(), any());
    }

    @Test
    void hotspotIdOutsideSourcePathIsHiddenAsNotFound() {
        UUID hotspotId = UUID.randomUUID();
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, source.getId()))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getHotspot(
                        organizer, exhibitionUuid, source.getId(), hotspotId));

        assertEquals(ErrorCode.HALL_HOTSPOT_NOT_FOUND, exception.getErrorCode());
    }

    private void stubEditableSource() {
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(panoramaRepository.findByIdAndHallId(source.getId(), hall.getId()))
                .thenReturn(Optional.of(source));
    }

    private HallPanorama panorama(String name) {
        return HallPanorama.builder()
                .id(UUID.randomUUID())
                .hall(hall)
                .name(name)
                .imageUrl("https://cdn.example/" + name)
                .imageKey(name + "-key")
                .fileSize(10L)
                .orderIndex(0)
                .isDefault(false)
                .build();
    }

    private UpsertHallHotspotRequest request(HallHotspotType type) {
        UpsertHallHotspotRequest request = new UpsertHallHotspotRequest();
        request.setType(type);
        request.setXPosition(1D);
        request.setYPosition(2D);
        request.setZPosition(3D);
        return request;
    }

    private Booth publishedBooth() {
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Published booth")
                .status(BoothStatus.PUBLISHED)
                .isTemplate(false)
                .thumbnailUrl("https://cdn.example/booth.jpg")
                .build();
    }

    private HallHotspot boothSlot(int slotIndex) {
        return HallHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(source)
                .type(HallHotspotType.BOOTH_ENTRY)
                .name("Slot " + slotIndex)
                .boothSlotIndex(slotIndex)
                .xPosition(1D)
                .yPosition(2D)
                .zPosition(3D)
                .build();
    }
}
