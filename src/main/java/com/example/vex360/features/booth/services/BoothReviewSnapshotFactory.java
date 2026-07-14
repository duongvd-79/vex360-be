package com.example.vex360.features.booth.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;

@Component
public class BoothReviewSnapshotFactory {
    public static final int SCHEMA_VERSION = 2;

    public BoothReviewSnapshot create(Booth booth) {
        List<BoothReviewSnapshot.PanoramaItem> panoramas = new ArrayList<>();
        List<BoothReviewSnapshot.HotspotItem> hotspots = new ArrayList<>();
        Map<UUID, BoothReviewSnapshot.ProductItem> products = new LinkedHashMap<>();
        Map<UUID, BoothReviewSnapshot.ProductContentItem> productContents = new LinkedHashMap<>();
        Map<UUID, BoothReviewSnapshot.MediaAssetItem> mediaAssets = new LinkedHashMap<>();
        List<BoothReviewSnapshot.PlacementItem> productPlacements = new ArrayList<>();
        List<BoothReviewSnapshot.PlacementItem> mediaPlacements = new ArrayList<>();

        for (Panorama panorama : sortedPanoramas(booth)) {
            panoramas.add(toPanorama(panorama));
            for (Hotspot hotspot : safeHotspots(panorama)) {
                hotspots.add(toHotspot(panorama, hotspot));
                Product product = hotspot.getProduct();
                if (hotspot.getType() == HotspotType.PRODUCT && product != null) {
                    products.computeIfAbsent(product.getId(), ignored -> toProduct(product));
                    for (ProductContent content : safeContents(product)) {
                        productContents.putIfAbsent(content.getId(), toProductContent(product, content));
                    }
                    productPlacements.add(toPlacement(panorama, hotspot, product.getId(), product.getName()));
                }
                MediaAsset mediaAsset = hotspot.getMediaAsset();
                if (hotspot.getType() == HotspotType.MEDIA && mediaAsset != null) {
                    mediaAssets.computeIfAbsent(mediaAsset.getId(), ignored -> toMediaAsset(mediaAsset));
                    mediaPlacements.add(toPlacement(panorama, hotspot, mediaAsset.getId(), mediaAsset.getName()));
                }
            }
        }

        return BoothReviewSnapshot.builder()
                .snapshotSchemaVersion(SCHEMA_VERSION)
                .booth(BoothReviewSnapshot.BoothItem.builder()
                        .id(booth.getId())
                        .name(booth.getName())
                        .description(booth.getDescription())
                        .thumbnailUrl(booth.getThumbnailUrl())
                        .backgroundMusicUrl(booth.getBackgroundMusicUrl())
                        .displayTemplateKey(booth.getDisplayTemplateKey())
                        .build())
                .panoramas(panoramas)
                .hotspots(hotspots)
                .products(new ArrayList<>(products.values()))
                .productContents(new ArrayList<>(productContents.values()))
                .mediaAssets(new ArrayList<>(mediaAssets.values()))
                .productPlacements(productPlacements)
                .mediaPlacements(mediaPlacements)
                .build();
    }

    private BoothReviewSnapshot.PanoramaItem toPanorama(Panorama panorama) {
        return BoothReviewSnapshot.PanoramaItem.builder()
                .id(panorama.getId())
                .name(panorama.getName())
                .imageUrl(panorama.getImageUrl())
                .imageKey(panorama.getImageKey())
                .orderIndex(panorama.getOrderIndex())
                .isDefault(panorama.getIsDefault())
                .build();
    }

    private BoothReviewSnapshot.HotspotItem toHotspot(Panorama panorama, Hotspot hotspot) {
        Product product = hotspot.getProduct();
        MediaAsset media = hotspot.getMediaAsset();
        Panorama target = hotspot.getTargetPanorama();
        return BoothReviewSnapshot.HotspotItem.builder()
                .id(hotspot.getId())
                .name(hotspot.getName())
                .type(hotspot.getType())
                .sourcePanoramaId(panorama.getId())
                .sourcePanoramaName(panorama.getName())
                .targetPanoramaId(target == null ? null : target.getId())
                .targetPanoramaName(target == null ? null : target.getName())
                .productId(product == null ? null : product.getId())
                .productName(product == null ? null : product.getName())
                .productSku(product == null ? null : product.getSku())
                .mediaAssetId(media == null ? null : media.getId())
                .mediaAssetName(media == null ? null : media.getName())
                .mediaAssetType(media == null ? null : media.getType())
                .mediaAssetUrl(media == null ? null : media.getUrl())
                .infoText(hotspot.getInfoText())
                .xPosition(hotspot.getXPosition())
                .yPosition(hotspot.getYPosition())
                .zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle())
                .scale(hotspot.getScale())
                .zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction())
                .infoContentType(hotspot.getInfoContentType())
                .cornerTlX(hotspot.getCornerTlX()).cornerTlY(hotspot.getCornerTlY()).cornerTlZ(hotspot.getCornerTlZ())
                .cornerTrX(hotspot.getCornerTrX()).cornerTrY(hotspot.getCornerTrY()).cornerTrZ(hotspot.getCornerTrZ())
                .cornerBlX(hotspot.getCornerBlX()).cornerBlY(hotspot.getCornerBlY()).cornerBlZ(hotspot.getCornerBlZ())
                .cornerBrX(hotspot.getCornerBrX()).cornerBrY(hotspot.getCornerBrY()).cornerBrZ(hotspot.getCornerBrZ())
                .build();
    }

    private BoothReviewSnapshot.ProductItem toProduct(Product product) {
        return BoothReviewSnapshot.ProductItem.builder()
                .id(product.getId()).name(product.getName()).sku(product.getSku())
                .description(product.getDescription()).thumbnailUrl(product.getThumbnailUrl())
                .price(product.getPrice()).currency(product.getCurrency()).status(product.getStatus())
                .build();
    }

    private BoothReviewSnapshot.ProductContentItem toProductContent(Product product, ProductContent content) {
        return BoothReviewSnapshot.ProductContentItem.builder()
                .id(content.getId()).productId(product.getId()).productName(product.getName())
                .type(content.getType()).url(content.getContentUrl()).mimeType(content.getMimeType())
                .fileSize(content.getFileSize()).orderIndex(content.getOrderIndex())
                .build();
    }

    private BoothReviewSnapshot.MediaAssetItem toMediaAsset(MediaAsset media) {
        return BoothReviewSnapshot.MediaAssetItem.builder()
                .id(media.getId()).name(media.getName()).type(media.getType()).url(media.getUrl())
                .mimeType(media.getMimeType()).fileSize(media.getFileSize())
                .build();
    }

    private BoothReviewSnapshot.PlacementItem toPlacement(
            Panorama panorama, Hotspot hotspot, UUID itemId, String itemName) {
        return BoothReviewSnapshot.PlacementItem.builder()
                .itemId(itemId).itemName(itemName)
                .hotspotId(hotspot.getId()).hotspotName(hotspot.getName())
                .panoramaId(panorama.getId()).panoramaName(panorama.getName())
                .build();
    }

    private List<Panorama> sortedPanoramas(Booth booth) {
        if (booth.getPanoramas() == null) {
            return List.of();
        }
        return booth.getPanoramas().stream()
                .sorted(Comparator.comparing(Panorama::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<Hotspot> safeHotspots(Panorama panorama) {
        return panorama.getHotspots() == null ? List.of() : panorama.getHotspots();
    }

    private List<ProductContent> safeContents(Product product) {
        return product.getContents() == null ? List.of() : product.getContents();
    }
}
