package com.example.vex360.features.hall.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.BoothSummary;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.HallScene;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.Hotspot;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.Item;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.ItemSummary;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.MediaAsset;
import com.example.vex360.features.hall.dtos.response.PublicExhibitionExperienceResponseDTO.Panorama;
import com.example.vex360.features.hall.entities.HallPublishedRevision;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.repositories.HallPublishedRevisionRepository;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallVisitorService {
    private final ExhibitionService exhibitionService;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final HallPublishedRevisionRepository publishedRevisionRepository;
    private final HallReviewDiffService reviewDiffService;
    private final VisitorBoothService visitorBoothService;

    @Transactional(readOnly = true)
    public PublicExhibitionExperienceResponseDTO getExperience(UUID exhibitionUuid) {
        ExhibitionResponseDTO exhibition = exhibitionService.getExhibitionByUuid(exhibitionUuid);
        if (!timelinePolicy.isExperienceActive(
                ExhibitionStatus.valueOf(exhibition.getStatus()),
                exhibition.getStartDate(),
                exhibition.getEndDate())) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionExperienceMode mode = exhibition.getExperienceMode();
        List<Booth> visibleBooths = mode == ExhibitionExperienceMode.WITH_BOOTHS
                ? visitorBoothService.findPublishedBoothsByFirstApproval(exhibitionUuid)
                : List.of();

        HallPublishedRevision revision = publishedRevisionRepository
                .findByHallExhibitionUuid(exhibitionUuid)
                .orElse(null);
        if (revision == null) {
            return toDefaultHallResponse(exhibition, mode, visibleBooths);
        }

        HallReviewSnapshot snapshot = reviewDiffService.readSnapshot(revision.getContentSnapshotJson());
        return toResponse(exhibition, snapshot, mode, visibleBooths);
    }

    private PublicExhibitionExperienceResponseDTO toDefaultHallResponse(
            ExhibitionResponseDTO exhibition,
            ExhibitionExperienceMode mode,
            List<Booth> visibleBooths) {
        List<BoothSummary> booths = mode == ExhibitionExperienceMode.WITH_BOOTHS
                ? visibleBooths.stream().map(this::toBoothSummary).toList()
                : null;
        return new PublicExhibitionExperienceResponseDTO(
                exhibition.getUuid(),
                exhibition.getName(),
                exhibition.getDescription(),
                exhibition.getCategory(),
                exhibition.getStartDate(),
                exhibition.getEndDate(),
                exhibition.getStatus(),
                mode,
                true,
                null,
                booths);
    }

    private PublicExhibitionExperienceResponseDTO toResponse(
            ExhibitionResponseDTO exhibition,
            HallReviewSnapshot snapshot,
            ExhibitionExperienceMode mode,
            List<Booth> visibleBooths) {
        Map<UUID, HallReviewSnapshot.MediaAssetItem> mediaById = new HashMap<>();
        for (HallReviewSnapshot.MediaAssetItem media : list(snapshot.getMediaAssets())) {
            mediaById.put(media.getId(), media);
        }

        Map<UUID, HallReviewSnapshot.ContentItem> itemById = new HashMap<>();
        List<Item> items = new ArrayList<>();
        for (HallReviewSnapshot.ContentItem item : list(snapshot.getItems())) {
            itemById.put(item.getId(), item);
            items.add(toItem(item, mediaById));
        }

        Map<UUID, String> panoramaNames = new HashMap<>();
        for (HallReviewSnapshot.PanoramaItem panorama : list(snapshot.getPanoramas())) {
            panoramaNames.put(panorama.getId(), panorama.getName());
        }

        Map<UUID, List<HallReviewSnapshot.HotspotItem>> hotspotsByPanorama = new LinkedHashMap<>();
        for (HallReviewSnapshot.HotspotItem hotspot : list(snapshot.getHotspots())) {
            if (mode == ExhibitionExperienceMode.STANDALONE
                    && hotspot.getType() == HallHotspotType.BOOTH_ENTRY) {
                continue;
            }
            hotspotsByPanorama
                    .computeIfAbsent(hotspot.getSourcePanoramaId(), ignored -> new ArrayList<>())
                    .add(hotspot);
        }

        List<Panorama> panoramas = list(snapshot.getPanoramas()).stream()
                .map(panorama -> new Panorama(
                        panorama.getId(),
                        panorama.getName(),
                        panorama.getImageUrl(),
                        panorama.getOrderIndex(),
                        panorama.getIsDefault(),
                        hotspotsByPanorama.getOrDefault(panorama.getId(), List.of()).stream()
                                .map(hotspot -> toHotspot(
                                        hotspot,
                                        panoramaNames,
                                        mediaById,
                                        itemById,
                                        visibleBooths))
                                .toList()))
                .toList();

        HallReviewSnapshot.HallItem hall = snapshot.getHall();
        List<BoothSummary> booths = mode == ExhibitionExperienceMode.WITH_BOOTHS
                ? visibleBooths.stream().map(this::toBoothSummary).toList()
                : null;
        return new PublicExhibitionExperienceResponseDTO(
                exhibition.getUuid(),
                exhibition.getName(),
                exhibition.getDescription(),
                exhibition.getCategory(),
                exhibition.getStartDate(),
                exhibition.getEndDate(),
                exhibition.getStatus(),
                mode,
                false,
                new HallScene(
                        hall.getId(),
                        hall.getName(),
                        hall.getDescription(),
                        hall.getBackgroundMusicUrl(),
                        hall.getBackgroundMusicFileName(),
                        hall.getBackgroundMusicFileSize(),
                        panoramas,
                        items),
                booths);
    }

    private Hotspot toHotspot(
            HallReviewSnapshot.HotspotItem hotspot,
            Map<UUID, String> panoramaNames,
            Map<UUID, HallReviewSnapshot.MediaAssetItem> mediaById,
            Map<UUID, HallReviewSnapshot.ContentItem> itemById,
            List<Booth> visibleBooths) {
        HallReviewSnapshot.ContentItem item = itemById.get(hotspot.getItemId());
        return new Hotspot(
                hotspot.getId(),
                hotspot.getType(),
                hotspot.getName(),
                hotspot.getSourcePanoramaId(),
                hotspot.getTargetPanoramaId(),
                panoramaNames.get(hotspot.getTargetPanoramaId()),
                toMediaAsset(mediaById.get(hotspot.getMediaAssetId())),
                item == null ? null : new ItemSummary(item.getId(), item.getName()),
                hotspot.getBoothSlotIndex(),
                resolveBooth(hotspot, visibleBooths),
                hotspot.getInfoText(),
                hotspot.getInfoContentType(),
                hotspot.getXPosition(),
                hotspot.getYPosition(),
                hotspot.getZPosition(),
                hotspot.getIconStyle(),
                hotspot.getScale(),
                hotspot.getZIndex(),
                hotspot.getMediaClickAction(),
                toCorners(hotspot));
    }

    private Item toItem(
            HallReviewSnapshot.ContentItem item,
            Map<UUID, HallReviewSnapshot.MediaAssetItem> mediaById) {
        return new Item(
                item.getId(),
                item.getName(),
                item.getDescription(),
                toMediaAsset(mediaById.get(item.getMediaAssetId())),
                item.getDisplayOrder());
    }

    private MediaAsset toMediaAsset(HallReviewSnapshot.MediaAssetItem media) {
        return media == null ? null : new MediaAsset(
                media.getId(),
                media.getName(),
                media.getType(),
                media.getUrl(),
                media.getMimeType(),
                media.getFileSize());
    }

    private BoothSummary resolveBooth(
            HallReviewSnapshot.HotspotItem hotspot,
            List<Booth> visibleBooths) {
        Integer slot = hotspot.getBoothSlotIndex();
        return hotspot.getType() == HallHotspotType.BOOTH_ENTRY
                && slot != null
                && slot >= 0
                && slot < visibleBooths.size()
                        ? toBoothSummary(visibleBooths.get(slot))
                        : null;
    }

    private BoothSummary toBoothSummary(Booth booth) {
        return new BoothSummary(booth.getId(), booth.getName(), booth.getThumbnailUrl());
    }

    private HallHotspotCornersDTO toCorners(HallReviewSnapshot.HotspotItem hotspot) {
        if (hotspot.getCornerTlX() == null || hotspot.getCornerTlY() == null || hotspot.getCornerTlZ() == null
                || hotspot.getCornerTrX() == null || hotspot.getCornerTrY() == null
                || hotspot.getCornerTrZ() == null
                || hotspot.getCornerBlX() == null || hotspot.getCornerBlY() == null
                || hotspot.getCornerBlZ() == null
                || hotspot.getCornerBrX() == null || hotspot.getCornerBrY() == null
                || hotspot.getCornerBrZ() == null) {
            return null;
        }
        return new HallHotspotCornersDTO(
                List.of(hotspot.getCornerTlX(), hotspot.getCornerTlY(), hotspot.getCornerTlZ()),
                List.of(hotspot.getCornerTrX(), hotspot.getCornerTrY(), hotspot.getCornerTrZ()),
                List.of(hotspot.getCornerBlX(), hotspot.getCornerBlY(), hotspot.getCornerBlZ()),
                List.of(hotspot.getCornerBrX(), hotspot.getCornerBrY(), hotspot.getCornerBrZ()));
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
