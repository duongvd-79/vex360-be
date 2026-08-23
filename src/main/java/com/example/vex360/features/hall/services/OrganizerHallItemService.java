package com.example.vex360.features.hall.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.hall.dtos.request.UpsertHallItemRequest;
import com.example.vex360.features.hall.dtos.response.HallItemResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizerHallItemService {
    private final HallItemRepository itemRepository;
    private final HallHotspotRepository hotspotRepository;
    private final ExhibitionHallService hallService;
    private final ExhibitorMediaAssetService mediaAssetService;
    private final HallSceneMapper sceneMapper;

    @Transactional(readOnly = true)
    public List<HallItemResponseDTO> getItems(User organizer, UUID exhibitionUuid) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return sceneMapper.toItemResponses(
                itemRepository.findByHallIdOrderByDisplayOrderAscNameAsc(hall.getId()));
    }

    @Transactional(readOnly = true)
    public HallItemResponseDTO getItem(User organizer, UUID exhibitionUuid, UUID itemId) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return sceneMapper.toItemResponse(findItem(hall, itemId));
    }

    @Transactional
    public HallItemResponseDTO createItem(
            User organizer,
            UUID exhibitionUuid,
            UpsertHallItemRequest request) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        MediaAsset mediaAsset = mediaAssetService.getMediaAssetForCurrentUser(
                organizer,
                request.getMediaAssetId());
        HallItem item = HallItem.builder().hall(hall).build();
        applyRequest(item, request, mediaAsset);
        return sceneMapper.toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public HallItemResponseDTO updateItem(
            User organizer,
            UUID exhibitionUuid,
            UUID itemId,
            UpsertHallItemRequest request) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallItem item = itemRepository.findByIdAndHallIdForUpdate(itemId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_ITEM_NOT_FOUND));
        MediaAsset mediaAsset = mediaAssetService.getMediaAssetForCurrentUser(
                organizer,
                request.getMediaAssetId());
        applyRequest(item, request, mediaAsset);
        return sceneMapper.toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public HallItemResponseDTO deleteItem(User organizer, UUID exhibitionUuid, UUID itemId) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallItem item = itemRepository.findByIdAndHallIdForUpdate(itemId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_ITEM_NOT_FOUND));
        if (hotspotRepository.existsByItemId(itemId)) {
            throw new AppException(ErrorCode.HALL_ITEM_IN_USE);
        }
        HallItemResponseDTO response = sceneMapper.toItemResponse(item);
        itemRepository.delete(item);
        return response;
    }

    private HallItem findItem(ExhibitionHall hall, UUID itemId) {
        return itemRepository.findByIdAndHallId(itemId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_ITEM_NOT_FOUND));
    }

    private void applyRequest(HallItem item, UpsertHallItemRequest request, MediaAsset mediaAsset) {
        item.setName(request.getName().trim());
        item.setDescription(request.getDescription());
        item.setMediaAsset(mediaAsset);
        item.setDisplayOrder(request.getDisplayOrder());
    }
}
