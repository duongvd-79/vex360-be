package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.hall.dtos.request.UpsertHallItemRequest;
import com.example.vex360.features.hall.dtos.response.HallItemResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.services.ExhibitionHallService;
import com.example.vex360.features.hall.services.OrganizerHallItemService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrganizerHallItemServiceUnitTest {
    @Mock
    private HallItemRepository itemRepository;
    @Mock
    private HallHotspotRepository hotspotRepository;
    @Mock
    private ExhibitionHallService hallService;
    @Mock
    private ExhibitorMediaAssetService mediaAssetService;

    private OrganizerHallItemService service;
    private User organizer;
    private UUID exhibitionUuid;
    private ExhibitionHall hall;
    private MediaAsset mediaAsset;

    @BeforeEach
    void setUp() {
        service = new OrganizerHallItemService(
                itemRepository,
                hotspotRepository,
                hallService,
                mediaAssetService,
                new HallSceneMapper());
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibitionUuid = UUID.randomUUID();
        hall = ExhibitionHall.builder().id(UUID.randomUUID()).name("Hall").build();
        mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .name("Artwork image")
                .type(MediaAssetType.IMAGE)
                .url("https://cdn.example/artwork.jpg")
                .build();
    }

    @Test
    void createsAndUpdatesItemWithOrganizerOwnedMedia() {
        UpsertHallItemRequest createRequest = request("  Artwork  ", "Description", 2);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, mediaAsset.getId()))
                .thenReturn(mediaAsset);
        when(itemRepository.save(any(HallItem.class)))
                .thenAnswer(invocation -> {
                    HallItem item = invocation.getArgument(0);
                    if (item.getId() == null) {
                        item.setId(UUID.randomUUID());
                    }
                    return item;
                });

        HallItemResponseDTO created = service.createItem(
                organizer, exhibitionUuid, createRequest);
        HallItem item = HallItem.builder()
                .id(created.getId())
                .hall(hall)
                .name(created.getName())
                .description(created.getDescription())
                .mediaAsset(mediaAsset)
                .displayOrder(created.getDisplayOrder())
                .build();
        UpsertHallItemRequest updateRequest = request("Updated", "New description", 0);
        when(itemRepository.findByIdAndHallIdForUpdate(item.getId(), hall.getId()))
                .thenReturn(Optional.of(item));

        HallItemResponseDTO updated = service.updateItem(
                organizer, exhibitionUuid, item.getId(), updateRequest);

        assertEquals("Artwork", created.getName());
        assertEquals(mediaAsset.getId(), created.getMediaAsset().getId());
        assertEquals("Updated", updated.getName());
        assertEquals(0, updated.getDisplayOrder());
    }

    @Test
    void listsAndReadsOnlyItemsInsideOwnedHall() {
        HallItem item = item("Artwork");
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(itemRepository.findByHallIdOrderByDisplayOrderAscNameAsc(hall.getId()))
                .thenReturn(List.of(item));
        when(itemRepository.findByIdAndHallId(item.getId(), hall.getId()))
                .thenReturn(Optional.of(item));

        List<HallItemResponseDTO> items = service.getItems(organizer, exhibitionUuid);
        HallItemResponseDTO found = service.getItem(organizer, exhibitionUuid, item.getId());

        assertEquals(1, items.size());
        assertEquals(item.getId(), found.getId());
    }

    @Test
    void rejectsForeignCompanyMediaBeforeSavingItem() {
        UpsertHallItemRequest request = request("Artwork", null, null);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(mediaAssetService.getMediaAssetForCurrentUser(organizer, mediaAsset.getId()))
                .thenThrow(new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createItem(organizer, exhibitionUuid, request));

        assertEquals(ErrorCode.MEDIA_ASSET_NOT_FOUND, exception.getErrorCode());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void hidesItemFromAnotherHallAsNotFound() {
        UUID itemId = UUID.randomUUID();
        when(hallService.findOwnedHallEntity(organizer, exhibitionUuid)).thenReturn(hall);
        when(itemRepository.findByIdAndHallId(itemId, hall.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getItem(organizer, exhibitionUuid, itemId));

        assertEquals(ErrorCode.HALL_ITEM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void refusesToDeleteReferencedItem() {
        HallItem item = item("Artwork");
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(itemRepository.findByIdAndHallIdForUpdate(item.getId(), hall.getId()))
                .thenReturn(Optional.of(item));
        when(hotspotRepository.existsByItemId(item.getId())).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteItem(organizer, exhibitionUuid, item.getId()));

        assertEquals(ErrorCode.HALL_ITEM_IN_USE, exception.getErrorCode());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void deletesUnreferencedItem() {
        HallItem item = item("Artwork");
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenReturn(hall);
        when(itemRepository.findByIdAndHallIdForUpdate(item.getId(), hall.getId()))
                .thenReturn(Optional.of(item));

        HallItemResponseDTO deleted = service.deleteItem(
                organizer, exhibitionUuid, item.getId());

        assertEquals(item.getId(), deleted.getId());
        verify(itemRepository).delete(item);
    }

    @Test
    void editLockFailureStopsBeforeItemLookup() {
        AppException locked = new AppException(ErrorCode.HALL_CHANGES_NOT_ALLOWED);
        when(hallService.findEditableHallForUpdate(organizer, exhibitionUuid)).thenThrow(locked);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createItem(
                        organizer, exhibitionUuid, request("Artwork", null, null)));

        assertSame(locked, exception);
        verify(mediaAssetService, never()).getMediaAssetForCurrentUser(any(), any());
    }

    private HallItem item(String name) {
        return HallItem.builder()
                .id(UUID.randomUUID())
                .hall(hall)
                .name(name)
                .mediaAsset(mediaAsset)
                .build();
    }

    private UpsertHallItemRequest request(String name, String description, Integer displayOrder) {
        return new UpsertHallItemRequest(
                name,
                description,
                mediaAsset.getId(),
                displayOrder);
    }
}
