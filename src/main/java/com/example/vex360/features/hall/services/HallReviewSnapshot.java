package com.example.vex360.features.hall.services;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HallReviewSnapshot {
    private Integer snapshotSchemaVersion;
    private HallItem hall;
    private List<PanoramaItem> panoramas;
    private List<HotspotItem> hotspots;
    private List<ContentItem> items;
    private List<MediaAssetItem> mediaAssets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HallItem {
        private UUID id;
        private UUID exhibitionUuid;
        private String exhibitionName;
        private ExhibitionExperienceMode experienceMode;
        private String name;
        private String description;
        private String backgroundMusicUrl;
        private String backgroundMusicFileName;
        private Long backgroundMusicFileSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PanoramaItem {
        private UUID id;
        private String name;
        private String imageUrl;
        private String imageKey;
        private Long fileSize;
        private Integer orderIndex;
        @JsonProperty("isDefault")
        private Boolean isDefault;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HotspotItem {
        private UUID id;
        private HallHotspotType type;
        private String name;
        private UUID sourcePanoramaId;
        private UUID targetPanoramaId;
        private UUID mediaAssetId;
        private UUID itemId;
        private Integer boothSlotIndex;
        private String infoText;
        private HallInfoContentType infoContentType;
        private Double xPosition;
        private Double yPosition;
        private Double zPosition;
        private String iconStyle;
        private Double scale;
        private Integer zIndex;
        private HotspotMediaClickAction mediaClickAction;
        private Double cornerTlX;
        private Double cornerTlY;
        private Double cornerTlZ;
        private Double cornerTrX;
        private Double cornerTrY;
        private Double cornerTrZ;
        private Double cornerBlX;
        private Double cornerBlY;
        private Double cornerBlZ;
        private Double cornerBrX;
        private Double cornerBrY;
        private Double cornerBrZ;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentItem {
        private UUID id;
        private String name;
        private String description;
        private UUID mediaAssetId;
        private Integer displayOrder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaAssetItem {
        private UUID id;
        private String name;
        private MediaAssetType type;
        private String url;
        private String publicId;
        private String mimeType;
        private Long fileSize;
    }
}
