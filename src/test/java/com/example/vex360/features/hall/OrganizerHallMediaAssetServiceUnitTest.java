package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallMediaAssetService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrganizerHallMediaAssetServiceUnitTest {
    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private ExhibitorMediaAssetService mediaAssetService;
    @Mock
    private DesignAssetReferenceService assetReferenceService;
    @Mock
    private HallHotspotRepository hotspotRepository;
    @Mock
    private HallItemRepository itemRepository;

    private OrganizerHallMediaAssetService service;
    private User organizer;
    private UUID exhibitionUuid;

    @BeforeEach
    void setUp() {
        service = new OrganizerHallMediaAssetService(
                hallService,
                mediaAssetService,
                assetReferenceService,
                hotspotRepository,
                itemRepository);
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibitionUuid = UUID.randomUUID();
    }

    @Test
    void listsOnlyAfterResolvingOwnedHallPath() {
        PageRequest pageable = PageRequest.of(0, 10);
        PageResponse<MediaAssetResponseDTO> expected = PageResponse.<MediaAssetResponseDTO>builder()
                .content(List.of())
                .page(0)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid))
                .thenReturn(ExhibitionHall.builder().id(UUID.randomUUID()).build());
        when(mediaAssetService.getMediaAssets(organizer, "image", pageable)).thenReturn(expected);

        PageResponse<MediaAssetResponseDTO> result = service.getMediaAssets(
                organizer, exhibitionUuid, "image", pageable);

        assertSame(expected, result);
        verify(hallService).findOwnedHallEntity(organizer, exhibitionUuid);
    }

    @Test
    void pendingReviewOrStartedExhibitionRejectsMediaMutation() {
        AppException locked = new AppException(ErrorCode.HALL_CHANGES_NOT_ALLOWED);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenThrow(locked);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createMediaAsset(organizer, exhibitionUuid, null, null));

        assertSame(locked, exception);
        verify(mediaAssetService, never()).createMediaAsset(any(), any(), any());
    }

    @Test
    void refusesToDeleteAssetReferencedByHallHotspot() {
        UUID assetId = UUID.randomUUID();
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid))
                .thenReturn(ExhibitionHall.builder().id(UUID.randomUUID()).build());
        when(hotspotRepository.existsByMediaAssetId(assetId)).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteMediaAsset(organizer, exhibitionUuid, assetId));

        assertEquals(ErrorCode.MEDIA_ASSET_IN_USE, exception.getErrorCode());
        verify(assetReferenceService, never()).deleteMediaAsset(any(), any());
    }

    @Test
    void refusesToDeleteAssetReferencedByHallItem() {
        UUID assetId = UUID.randomUUID();
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid))
                .thenReturn(ExhibitionHall.builder().id(UUID.randomUUID()).build());
        when(itemRepository.existsByMediaAssetId(assetId)).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteMediaAsset(organizer, exhibitionUuid, assetId));

        assertEquals(ErrorCode.MEDIA_ASSET_IN_USE, exception.getErrorCode());
        verify(assetReferenceService, never()).deleteMediaAsset(any(), any());
    }

    @Test
    void safeDeleteDelegatesToExistingReferenceCleanupFlow() {
        UUID assetId = UUID.randomUUID();
        MediaAssetResponseDTO expected = new MediaAssetResponseDTO();
        expected.setId(assetId);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid))
                .thenReturn(ExhibitionHall.builder().id(UUID.randomUUID()).build());
        when(assetReferenceService.deleteMediaAsset(organizer, assetId)).thenReturn(expected);

        MediaAssetResponseDTO result = service.deleteMediaAsset(
                organizer, exhibitionUuid, assetId);

        assertSame(expected, result);
    }
}
