package com.example.vex360.features.hall.services;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HallReviewSnapshotFactory {
    public static final int SCHEMA_VERSION = 3;

    private final HallPanoramaRepository panoramaRepository;
    private final HallHotspotRepository hotspotRepository;
    private final HallItemRepository itemRepository;

    public HallReviewSnapshot create(ExhibitionHall hall) {
        List<HallPanorama> panoramas = panoramaRepository.findByHallIdOrderByOrderIndexAsc(hall.getId());
        List<HallHotspot> hotspots = hotspotRepository.findSnapshotHotspotsByHallId(hall.getId());
        List<HallItem> items = itemRepository.findSnapshotItemsByHallId(hall.getId());
        Map<UUID, MediaAsset> mediaAssets = new LinkedHashMap<>();
        hotspots.stream().map(HallHotspot::getMediaAsset).filter(media -> media != null)
                .forEach(media -> mediaAssets.putIfAbsent(media.getId(), media));
        items.stream().map(HallItem::getMediaAsset).filter(media -> media != null)
                .forEach(media -> mediaAssets.putIfAbsent(media.getId(), media));

        return HallReviewSnapshot.builder()
                .snapshotSchemaVersion(SCHEMA_VERSION)
                .hall(HallReviewSnapshot.HallItem.builder()
                        .id(hall.getId())
                        .exhibitionUuid(hall.getExhibition().getUuid())
                        .exhibitionName(hall.getExhibition().getName())
                        .experienceMode(hall.getExhibition().getExperienceMode())
                        .name(hall.getName())
                        .description(hall.getDescription())
                        .backgroundMusicUrl(hall.getBackgroundMusicUrl())
                        .backgroundMusicFileName(hall.getBackgroundMusicFileName())
                        .backgroundMusicFileSize(hall.getBackgroundMusicFileSize())
                        .build())
                .panoramas(panoramas.stream().map(this::toPanorama).toList())
                .hotspots(hotspots.stream().map(this::toHotspot).toList())
                .items(items.stream().map(this::toItem).toList())
                .mediaAssets(mediaAssets.values().stream()
                        .sorted(Comparator.comparing(media -> media.getId().toString()))
                        .map(this::toMediaAsset)
                        .toList())
                .build();
    }

    private HallReviewSnapshot.PanoramaItem toPanorama(HallPanorama panorama) {
        return HallReviewSnapshot.PanoramaItem.builder()
                .id(panorama.getId()).name(panorama.getName())
                .imageUrl(panorama.getImageUrl()).imageKey(panorama.getImageKey())
                .fileSize(panorama.getFileSize()).orderIndex(panorama.getOrderIndex())
                .isDefault(panorama.getIsDefault()).build();
    }

    private HallReviewSnapshot.HotspotItem toHotspot(HallHotspot hotspot) {
        return HallReviewSnapshot.HotspotItem.builder()
                .id(hotspot.getId()).type(hotspot.getType()).name(hotspot.getName())
                .sourcePanoramaId(hotspot.getSourcePanorama().getId())
                .targetPanoramaId(hotspot.getTargetPanorama() == null ? null : hotspot.getTargetPanorama().getId())
                .mediaAssetId(hotspot.getMediaAsset() == null ? null : hotspot.getMediaAsset().getId())
                .itemId(hotspot.getItem() == null ? null : hotspot.getItem().getId())
                .boothSlotIndex(hotspot.getBoothSlotIndex()).infoText(hotspot.getInfoText())
                .infoContentType(hotspot.getInfoContentType())
                .xPosition(hotspot.getXPosition()).yPosition(hotspot.getYPosition()).zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle()).scale(hotspot.getScale()).zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction())
                .cornerTlX(hotspot.getCornerTlX()).cornerTlY(hotspot.getCornerTlY()).cornerTlZ(hotspot.getCornerTlZ())
                .cornerTrX(hotspot.getCornerTrX()).cornerTrY(hotspot.getCornerTrY()).cornerTrZ(hotspot.getCornerTrZ())
                .cornerBlX(hotspot.getCornerBlX()).cornerBlY(hotspot.getCornerBlY()).cornerBlZ(hotspot.getCornerBlZ())
                .cornerBrX(hotspot.getCornerBrX()).cornerBrY(hotspot.getCornerBrY()).cornerBrZ(hotspot.getCornerBrZ())
                .build();
    }

    private HallReviewSnapshot.ContentItem toItem(HallItem item) {
        return HallReviewSnapshot.ContentItem.builder()
                .id(item.getId()).name(item.getName()).description(item.getDescription())
                .mediaAssetId(item.getMediaAsset().getId()).displayOrder(item.getDisplayOrder()).build();
    }

    private HallReviewSnapshot.MediaAssetItem toMediaAsset(MediaAsset media) {
        return HallReviewSnapshot.MediaAssetItem.builder()
                .id(media.getId()).name(media.getName()).type(media.getType()).url(media.getUrl())
                .publicId(media.getPublicId()).mimeType(media.getMimeType()).fileSize(media.getFileSize()).build();
    }
}
