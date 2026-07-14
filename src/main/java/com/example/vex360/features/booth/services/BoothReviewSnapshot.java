package com.example.vex360.features.booth.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
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
public class BoothReviewSnapshot {
    private Integer snapshotSchemaVersion;
    private BoothItem booth;
    private List<PanoramaItem> panoramas;
    private List<HotspotItem> hotspots;
    private List<ProductItem> products;
    private List<ProductContentItem> productContents;
    private List<MediaAssetItem> mediaAssets;
    private List<PlacementItem> productPlacements;
    private List<PlacementItem> mediaPlacements;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoothItem {
        private UUID id;
        private String name;
        private String description;
        private String thumbnailUrl;
        private String backgroundMusicUrl;
        private String displayTemplateKey;
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
        private String name;
        private HotspotType type;
        private UUID sourcePanoramaId;
        private String sourcePanoramaName;
        private UUID targetPanoramaId;
        private String targetPanoramaName;
        private UUID productId;
        private String productName;
        private String productSku;
        private UUID mediaAssetId;
        private String mediaAssetName;
        private MediaAssetType mediaAssetType;
        private String mediaAssetUrl;
        private String infoText;
        private Double xPosition;
        private Double yPosition;
        private Double zPosition;
        private String iconStyle;
        private Double scale;
        private Integer zIndex;
        private HotspotMediaClickAction mediaClickAction;
        private HotspotInfoContentType infoContentType;
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
    public static class ProductItem {
        private UUID id;
        private String name;
        private String sku;
        private String description;
        private String thumbnailUrl;
        private BigDecimal price;
        private String currency;
        private ProductStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductContentItem {
        private UUID id;
        private UUID productId;
        private String productName;
        private ProductContentType type;
        private String url;
        private String mimeType;
        private Long fileSize;
        private Integer orderIndex;
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
        private String mimeType;
        private Long fileSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlacementItem {
        private UUID itemId;
        private String itemName;
        private String sku;
        private String description;
        private String thumbnailUrl;
        private BigDecimal price;
        private String currency;
        private ProductStatus productStatus;
        private MediaAssetType mediaAssetType;
        private String url;
        private String mimeType;
        private Long fileSize;
        private UUID hotspotId;
        private String hotspotName;
        private UUID panoramaId;
        private String panoramaName;
    }
}
