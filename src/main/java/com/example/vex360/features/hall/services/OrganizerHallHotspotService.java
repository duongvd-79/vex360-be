package com.example.vex360.features.hall.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.dtos.request.UpsertHallHotspotRequest;
import com.example.vex360.features.hall.dtos.response.HallHotspotResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizerHallHotspotService {
    private final HallPanoramaRepository panoramaRepository;
    private final HallHotspotRepository hotspotRepository;
    private final HallItemRepository itemRepository;
    private final ExhibitionHallService hallService;
    private final ExhibitorMediaAssetService mediaAssetService;
    private final VisitorBoothService visitorBoothService;
    private final HallSceneMapper sceneMapper;

    @Transactional(readOnly = true)
    public List<HallHotspotResponseDTO> getHotspots(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        HallPanorama panorama = findPanorama(hall, panoramaId);
        return toResponses(
                hall,
                hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(panorama.getId()));
    }

    @Transactional(readOnly = true)
    public HallHotspotResponseDTO getHotspot(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId,
            UUID hotspotId) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        HallPanorama panorama = findPanorama(hall, panoramaId);
        HallHotspot hotspot = hotspotRepository
                .findByIdAndSourcePanoramaId(hotspotId, panorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_HOTSPOT_NOT_FOUND));
        return toResponse(hall, hotspot);
    }

    @Transactional
    public HallHotspotResponseDTO createHotspot(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId,
            UpsertHallHotspotRequest request) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallPanorama source = findPanorama(hall, panoramaId);
        HallHotspot hotspot = HallHotspot.builder().sourcePanorama(source).build();
        applyRequest(hotspot, request, hall, organizer);
        return toResponse(hall, hotspotRepository.save(hotspot));
    }

    @Transactional
    public HallHotspotResponseDTO updateHotspot(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId,
            UUID hotspotId,
            UpsertHallHotspotRequest request) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallPanorama source = findPanorama(hall, panoramaId);
        HallHotspot hotspot = hotspotRepository
                .findByIdAndSourcePanoramaIdForUpdate(hotspotId, source.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_HOTSPOT_NOT_FOUND));
        applyRequest(hotspot, request, hall, organizer);
        return toResponse(hall, hotspotRepository.save(hotspot));
    }

    @Transactional
    public HallHotspotResponseDTO deleteHotspot(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId,
            UUID hotspotId) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallPanorama source = findPanorama(hall, panoramaId);
        HallHotspot hotspot = hotspotRepository
                .findByIdAndSourcePanoramaIdForUpdate(hotspotId, source.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_HOTSPOT_NOT_FOUND));
        HallHotspotResponseDTO response = toResponse(hall, hotspot);
        hotspotRepository.delete(hotspot);
        return response;
    }

    private void applyRequest(
            HallHotspot hotspot,
            UpsertHallHotspotRequest request,
            ExhibitionHall hall,
            User organizer) {
        if (request == null || request.getType() == null
                || request.getXPosition() == null
                || request.getYPosition() == null
                || request.getZPosition() == null) {
            throw new AppException(ErrorCode.HALL_HOTSPOT_REQUEST_INVALID);
        }

        hotspot.setType(request.getType());
        hotspot.setXPosition(request.getXPosition());
        hotspot.setYPosition(request.getYPosition());
        hotspot.setZPosition(request.getZPosition());
        hotspot.setIconStyle(trimToNull(request.getIconStyle()));
        hotspot.setScale(request.getScale());
        hotspot.setZIndex(request.getZIndex());
        hotspot.setTargetPanorama(null);
        hotspot.setMediaAsset(null);
        hotspot.setItem(null);
        hotspot.setBoothSlotIndex(null);
        hotspot.setInfoText(null);
        hotspot.setInfoContentType(null);
        hotspot.setMediaClickAction(null);
        clearCorners(hotspot);

        switch (request.getType()) {
            case NAV -> applyNavigation(hotspot, request, hall);
            case INFO -> applyInfo(hotspot, request, hall, organizer);
            case MEDIA -> applyMedia(hotspot, request, organizer);
            case ITEM -> applyItem(hotspot, request, hall);
            case BOOTH_ENTRY -> applyBoothEntry(hotspot, request, hall, organizer);
        }
    }

    private void applyNavigation(
            HallHotspot hotspot,
            UpsertHallHotspotRequest request,
            ExhibitionHall hall) {
        if (request.getTargetPanoramaId() == null) {
            throw new AppException(ErrorCode.HALL_NAV_TARGET_REQUIRED);
        }
        HallPanorama target = findPanorama(hall, request.getTargetPanoramaId());
        if (target.getId().equals(hotspot.getSourcePanorama().getId())) {
            throw new AppException(ErrorCode.HALL_HOTSPOT_REQUEST_INVALID);
        }
        hotspot.setTargetPanorama(target);
        hotspot.setName(resolveName(request.getName(), target.getName()));
    }

    private void applyInfo(
            HallHotspot hotspot,
            UpsertHallHotspotRequest request,
            ExhibitionHall hall,
            User organizer) {
        MediaAsset inferredMedia = null;
        HallInfoContentType contentType = request.getInfoContentType();
        if (contentType == null) {
            if (request.getItemId() != null) {
                contentType = HallInfoContentType.ITEM;
            } else if (request.getMediaAssetId() != null) {
                inferredMedia = mediaAssetService.getMediaAssetForCurrentUser(
                        organizer,
                        request.getMediaAssetId());
                contentType = inferredMedia.getType() == MediaAssetType.VIDEO
                        ? HallInfoContentType.VIDEO
                        : HallInfoContentType.IMAGE;
            } else if (trimToNull(request.getInfoText()) != null) {
                contentType = HallInfoContentType.TEXT;
            } else {
                contentType = HallInfoContentType.NONE;
            }
        }

        hotspot.setInfoContentType(contentType);
        hotspot.setName(resolveName(request.getName(), "Info"));
        switch (contentType) {
            case NONE -> {
            }
            case TEXT -> {
                String infoText = trimToNull(request.getInfoText());
                if (infoText == null) {
                    throw new AppException(ErrorCode.HALL_INFO_CONTENT_INVALID);
                }
                hotspot.setInfoText(infoText);
            }
            case IMAGE -> hotspot.setMediaAsset(requireInfoMedia(
                    request, organizer, inferredMedia, MediaAssetType.IMAGE));
            case VIDEO -> hotspot.setMediaAsset(requireInfoMedia(
                    request, organizer, inferredMedia, MediaAssetType.VIDEO));
            case ITEM -> {
                if (request.getItemId() == null) {
                    throw new AppException(ErrorCode.HALL_ITEM_REQUIRED);
                }
                HallItem item = itemRepository.findByIdAndHallId(request.getItemId(), hall.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.HALL_ITEM_NOT_FOUND));
                hotspot.setItem(item);
            }
        }
    }

    private MediaAsset requireInfoMedia(
            UpsertHallHotspotRequest request,
            User organizer,
            MediaAsset inferredMedia,
            MediaAssetType expectedType) {
        if (request.getMediaAssetId() == null) {
            throw new AppException(ErrorCode.HALL_MEDIA_REQUIRED);
        }
        MediaAsset mediaAsset = inferredMedia == null
                ? mediaAssetService.getMediaAssetForCurrentUser(organizer, request.getMediaAssetId())
                : inferredMedia;
        if (mediaAsset.getType() != expectedType) {
            throw new AppException(ErrorCode.HALL_INFO_CONTENT_INVALID);
        }
        return mediaAsset;
    }

    private void applyMedia(HallHotspot hotspot, UpsertHallHotspotRequest request, User organizer) {
        if (request.getMediaAssetId() == null) {
            throw new AppException(ErrorCode.HALL_MEDIA_REQUIRED);
        }
        MediaAsset mediaAsset = mediaAssetService.getMediaAssetForCurrentUser(
                organizer,
                request.getMediaAssetId());
        hotspot.setMediaAsset(mediaAsset);
        hotspot.setMediaClickAction(request.getMediaClickAction() == null
                ? HotspotMediaClickAction.DEFAULT
                : request.getMediaClickAction());
        hotspot.setName(resolveName(request.getName(), mediaAsset.getName()));
        applyCorners(hotspot, request.getCorners());
    }

    private void applyItem(
            HallHotspot hotspot,
            UpsertHallHotspotRequest request,
            ExhibitionHall hall) {
        if (request.getItemId() == null) {
            throw new AppException(ErrorCode.HALL_ITEM_REQUIRED);
        }
        HallItem item = itemRepository.findByIdAndHallId(request.getItemId(), hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_ITEM_NOT_FOUND));
        hotspot.setItem(item);
        hotspot.setName(resolveName(request.getName(), item.getName()));
        applyCorners(hotspot, request.getCorners());
    }

    private void applyBoothEntry(
            HallHotspot hotspot,
            UpsertHallHotspotRequest request,
            ExhibitionHall hall,
            User organizer) {
        if (hall.getExhibition().getExperienceMode() != ExhibitionExperienceMode.WITH_BOOTHS) {
            throw new AppException(ErrorCode.HALL_BOOTH_ENTRY_NOT_ALLOWED);
        }
        Integer slotIndex = request.getBoothSlotIndex();
        if (slotIndex == null || slotIndex < 0) {
            throw new AppException(ErrorCode.HALL_BOOTH_SLOT_INVALID);
        }
        if (hotspotRepository.existsBoothSlot(
                hall.getId(), HallHotspotType.BOOTH_ENTRY, slotIndex, hotspot.getId())) {
            throw new AppException(ErrorCode.HALL_BOOTH_SLOT_DUPLICATED);
        }
        hotspot.setBoothSlotIndex(slotIndex);
        if (request.getMediaAssetId() != null) {
            MediaAsset mediaAsset = mediaAssetService.getMediaAssetForCurrentUser(
                    organizer,
                    request.getMediaAssetId());
            hotspot.setMediaAsset(mediaAsset);
        }
        applyCorners(hotspot, request.getCorners());

        hotspot.setName(resolveName(request.getName(), "Booth slot " + (slotIndex + 1)));
    }

    private List<HallHotspotResponseDTO> toResponses(
            ExhibitionHall hall,
            List<HallHotspot> hotspots) {
        List<Booth> booths = hotspots.stream().anyMatch(this::isBoothEntry)
                ? findVisibleBooths(hall)
                : List.of();
        return hotspots.stream()
                .map(hotspot -> sceneMapper.toHotspotResponse(
                        hotspot,
                        resolveBooth(hotspot, booths)))
                .toList();
    }

    private HallHotspotResponseDTO toResponse(ExhibitionHall hall, HallHotspot hotspot) {
        List<Booth> booths = isBoothEntry(hotspot) ? findVisibleBooths(hall) : List.of();
        return sceneMapper.toHotspotResponse(hotspot, resolveBooth(hotspot, booths));
    }

    private List<Booth> findVisibleBooths(ExhibitionHall hall) {
        if (hall.getExhibition().getExperienceMode() != ExhibitionExperienceMode.WITH_BOOTHS) {
            return List.of();
        }
        return visitorBoothService.findPublishedBoothsByFirstApproval(
                hall.getExhibition().getUuid());
    }

    private Booth resolveBooth(HallHotspot hotspot, List<Booth> booths) {
        Integer slotIndex = hotspot.getBoothSlotIndex();
        return isBoothEntry(hotspot) && slotIndex != null && slotIndex >= 0 && slotIndex < booths.size()
                ? booths.get(slotIndex)
                : null;
    }

    private boolean isBoothEntry(HallHotspot hotspot) {
        return hotspot.getType() == HallHotspotType.BOOTH_ENTRY;
    }

    private void applyCorners(HallHotspot hotspot, HallHotspotCornersDTO corners) {
        if (corners == null) {
            return;
        }
        if (!isCorner(corners.getTl()) || !isCorner(corners.getTr())
                || !isCorner(corners.getBl()) || !isCorner(corners.getBr())) {
            throw new AppException(ErrorCode.HALL_HOTSPOT_CORNERS_INVALID);
        }
        hotspot.setCornerTlX(corners.getTl().get(0));
        hotspot.setCornerTlY(corners.getTl().get(1));
        hotspot.setCornerTlZ(corners.getTl().get(2));
        hotspot.setCornerTrX(corners.getTr().get(0));
        hotspot.setCornerTrY(corners.getTr().get(1));
        hotspot.setCornerTrZ(corners.getTr().get(2));
        hotspot.setCornerBlX(corners.getBl().get(0));
        hotspot.setCornerBlY(corners.getBl().get(1));
        hotspot.setCornerBlZ(corners.getBl().get(2));
        hotspot.setCornerBrX(corners.getBr().get(0));
        hotspot.setCornerBrY(corners.getBr().get(1));
        hotspot.setCornerBrZ(corners.getBr().get(2));
    }

    private boolean isCorner(List<Double> corner) {
        return corner != null && corner.size() == 3
                && corner.get(0) != null && corner.get(1) != null && corner.get(2) != null;
    }

    private void clearCorners(HallHotspot hotspot) {
        hotspot.setCornerTlX(null);
        hotspot.setCornerTlY(null);
        hotspot.setCornerTlZ(null);
        hotspot.setCornerTrX(null);
        hotspot.setCornerTrY(null);
        hotspot.setCornerTrZ(null);
        hotspot.setCornerBlX(null);
        hotspot.setCornerBlY(null);
        hotspot.setCornerBlZ(null);
        hotspot.setCornerBrX(null);
        hotspot.setCornerBrY(null);
        hotspot.setCornerBrZ(null);
    }

    private HallPanorama findPanorama(ExhibitionHall hall, UUID panoramaId) {
        return panoramaRepository.findByIdAndHallId(panoramaId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_PANORAMA_NOT_FOUND));
    }

    private String resolveName(String requestedName, String fallbackName) {
        String name = trimToNull(requestedName);
        if (name != null) {
            return name;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName.trim();
        }
        throw new AppException(ErrorCode.HALL_HOTSPOT_NAME_REQUIRED);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
