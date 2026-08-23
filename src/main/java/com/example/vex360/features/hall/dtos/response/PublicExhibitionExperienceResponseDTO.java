package com.example.vex360.features.hall.dtos.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicExhibitionExperienceResponseDTO(
        UUID exhibitionUuid,
        String exhibitionName,
        String exhibitionDescription,
        String category,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        ExhibitionExperienceMode experienceMode,
        boolean usesDefaultHall,
        HallScene hall,
        List<BoothSummary> booths) {

    public record HallScene(
            UUID id,
            String name,
            String description,
            String backgroundMusicUrl,
            String backgroundMusicFileName,
            Long backgroundMusicFileSize,
            List<Panorama> panoramas,
            List<Item> items) {
    }

    public record Panorama(
            UUID id,
            String name,
            String imageUrl,
            Integer orderIndex,
            Boolean isDefault,
            List<Hotspot> hotspots) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Hotspot(
            UUID id,
            HallHotspotType type,
            String name,
            UUID sourcePanoramaId,
            UUID targetPanoramaId,
            String targetPanoramaName,
            MediaAsset mediaAsset,
            ItemSummary item,
            Integer boothSlotIndex,
            BoothSummary targetBooth,
            String infoText,
            HallInfoContentType infoContentType,
            Double xPosition,
            Double yPosition,
            Double zPosition,
            String iconStyle,
            Double scale,
            Integer zIndex,
            HotspotMediaClickAction mediaClickAction,
            HallHotspotCornersDTO corners) {
    }

    public record Item(
            UUID id,
            String name,
            String description,
            MediaAsset mediaAsset,
            Integer displayOrder) {
    }

    public record ItemSummary(UUID id, String name) {
    }

    public record MediaAsset(
            UUID id,
            String name,
            MediaAssetType type,
            String url,
            String mimeType,
            Long fileSize) {
    }

    public record BoothSummary(UUID id, String name, String thumbnailUrl) {
    }
}
