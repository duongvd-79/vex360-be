package com.example.vex360.features.hall.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.dtos.response.HallBoothSummaryDTO;
import com.example.vex360.features.hall.dtos.response.HallHotspotResponseDTO;
import com.example.vex360.features.hall.dtos.response.HallItemResponseDTO;
import com.example.vex360.features.hall.dtos.response.HallItemSummaryDTO;
import com.example.vex360.features.hall.dtos.response.HallPanoramaResponseDTO;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallItem;
import com.example.vex360.features.hall.entities.HallPanorama;

@Component
public class HallSceneMapper {
    public HallPanoramaResponseDTO toPanoramaResponse(HallPanorama panorama) {
        return HallPanoramaResponseDTO.builder()
                .id(panorama.getId())
                .hallId(panorama.getHall().getId())
                .name(panorama.getName())
                .imageUrl(panorama.getImageUrl())
                .imageKey(panorama.getImageKey())
                .fileSize(panorama.getFileSize())
                .orderIndex(panorama.getOrderIndex())
                .isDefault(panorama.getIsDefault())
                .createdAt(panorama.getCreatedAt())
                .updatedAt(panorama.getUpdatedAt())
                .build();
    }

    public List<HallPanoramaResponseDTO> toPanoramaResponses(List<HallPanorama> panoramas) {
        return panoramas.stream().map(this::toPanoramaResponse).toList();
    }

    public HallHotspotResponseDTO toHotspotResponse(HallHotspot hotspot) {
        return toHotspotResponse(hotspot, null);
    }

    public HallHotspotResponseDTO toHotspotResponse(HallHotspot hotspot, Booth targetBooth) {
        HallPanorama target = hotspot.getTargetPanorama();
        return HallHotspotResponseDTO.builder()
                .id(hotspot.getId())
                .type(hotspot.getType())
                .name(hotspot.getName())
                .sourcePanoramaId(hotspot.getSourcePanorama().getId())
                .targetPanoramaId(target == null ? null : target.getId())
                .targetPanoramaName(target == null ? null : target.getName())
                .mediaAsset(toMediaAssetResponse(hotspot.getMediaAsset()))
                .item(toItemSummary(hotspot.getItem()))
                .boothSlotIndex(hotspot.getBoothSlotIndex())
                .targetBooth(toBoothSummary(targetBooth))
                .infoText(hotspot.getInfoText())
                .xPosition(hotspot.getXPosition())
                .yPosition(hotspot.getYPosition())
                .zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle())
                .scale(hotspot.getScale())
                .zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction())
                .infoContentType(hotspot.getInfoContentType())
                .corners(toCorners(hotspot))
                .build();
    }

    public List<HallHotspotResponseDTO> toHotspotResponses(List<HallHotspot> hotspots) {
        return hotspots.stream().map(this::toHotspotResponse).toList();
    }

    public HallItemResponseDTO toItemResponse(HallItem item) {
        return HallItemResponseDTO.builder()
                .id(item.getId())
                .hallId(item.getHall().getId())
                .name(item.getName())
                .description(item.getDescription())
                .mediaAsset(toMediaAssetResponse(item.getMediaAsset()))
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public List<HallItemResponseDTO> toItemResponses(List<HallItem> items) {
        return items.stream().map(this::toItemResponse).toList();
    }

    public MediaAssetResponseDTO toMediaAssetResponse(MediaAsset mediaAsset) {
        if (mediaAsset == null) {
            return null;
        }
        Company company = mediaAsset.getCompany();
        return new MediaAssetResponseDTO(
                mediaAsset.getId(),
                company == null ? null : company.getId(),
                mediaAsset.getName(),
                mediaAsset.getType(),
                mediaAsset.getUrl(),
                mediaAsset.getPublicId(),
                mediaAsset.getMimeType(),
                mediaAsset.getFileSize(),
                mediaAsset.getCreatedAt());
    }

    private HallItemSummaryDTO toItemSummary(HallItem item) {
        return item == null ? null : new HallItemSummaryDTO(item.getId(), item.getName());
    }

    private HallBoothSummaryDTO toBoothSummary(Booth booth) {
        if (booth == null) {
            return null;
        }
        return new HallBoothSummaryDTO(booth.getId(), booth.getName(), booth.getThumbnailUrl());
    }

    private HallHotspotCornersDTO toCorners(HallHotspot hotspot) {
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
}
