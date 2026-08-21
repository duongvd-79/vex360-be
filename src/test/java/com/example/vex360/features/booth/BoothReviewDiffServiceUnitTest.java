package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.dtos.response.BoothReviewChangeItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewChangeSummaryDTO;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewChangeScope;
import com.example.vex360.features.booth.enums.BoothReviewChangeType;
import com.example.vex360.features.booth.enums.BoothReviewComparisonCompleteness;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothReviewDiffService;
import com.example.vex360.features.booth.services.BoothReviewSnapshot;
import com.example.vex360.features.booth.services.BoothReviewSnapshotFactory;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BoothReviewDiffServiceUnitTest {
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PANORAMA_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CHANGED_PANORAMA_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final BoothReviewDiffService service = new BoothReviewDiffService(
            JsonMapper.builder().build());

    @Test
    void schema2DiffCoversEveryScopeAndDoesNotDuplicateProductMetadata() {
        UUID panoramaId = UUID.randomUUID();
        UUID hotspotId = UUID.randomUUID();
        UUID oldProductId = UUID.randomUUID();
        UUID newProductId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID removedContentId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        BoothReviewSnapshot previous = snapshot(panoramaId, hotspotId, oldProductId, contentId, removedContentId, mediaId, false);
        BoothReviewSnapshot current = snapshot(panoramaId, hotspotId, newProductId, contentId, null, mediaId, true);
        BoothReviewRequest previousRequest = BoothReviewRequest.builder()
                .id(UUID.randomUUID()).versionNumber(7).contentSnapshotJson(service.writeJson(previous)).build();

        BoothReviewChangeSummaryDTO result = service.buildSummary(current, previousRequest, 8);

        assertEquals(BoothReviewComparisonCompleteness.FULL, result.getComparisonCompleteness());
        assertEquals(previousRequest.getId(), result.getComparedToRequestId());
        assertEquals(7, result.getComparedToVersionNumber());
        assertEquals(8, result.getVersionNumber());
        assertTrue(result.getItems().stream().map(BoothReviewChangeItemDTO::getScope).toList()
                .containsAll(List.of(
                        BoothReviewChangeScope.BOOTH,
                        BoothReviewChangeScope.PANORAMA,
                        BoothReviewChangeScope.HOTSPOT,
                        BoothReviewChangeScope.PRODUCT,
                        BoothReviewChangeScope.PRODUCT_CONTENT,
                        BoothReviewChangeScope.MEDIA_ASSET,
                        BoothReviewChangeScope.PRODUCT_PLACEMENT,
                        BoothReviewChangeScope.MEDIA_PLACEMENT)));
        assertEquals(1, result.getItems().stream()
                .filter(item -> item.getScope() == BoothReviewChangeScope.PRODUCT
                        && item.getType() == BoothReviewChangeType.ADDED
                        && newProductId.equals(item.getItemId()))
                .count());
        assertTrue(result.getItems().stream().anyMatch(item ->
                item.getScope() == BoothReviewChangeScope.PRODUCT_CONTENT
                        && item.getType() == BoothReviewChangeType.MODIFIED
                        && item.getFieldChanges().stream().anyMatch(field -> "orderIndex".equals(field.getField()))));
        assertTrue(result.getItems().stream().anyMatch(item ->
                item.getScope() == BoothReviewChangeScope.PRODUCT_CONTENT
                        && item.getType() == BoothReviewChangeType.REMOVED
                        && removedContentId.equals(item.getItemId())));
        assertEquals(result.getAddedCount() + result.getModifiedCount() + result.getRemovedCount(),
                result.getTotalCount());
    }

    @Test
    void everyScopeReportsAddedItems() {
        for (BoothReviewChangeScope scope : BoothReviewChangeScope.values()) {
            assertScopeChange(scope, BoothReviewChangeType.ADDED,
                    scopeSnapshot(scope, false, false),
                    scopeSnapshot(scope, true, false));
        }
    }

    @Test
    void everyScopeReportsModifiedItems() {
        for (BoothReviewChangeScope scope : BoothReviewChangeScope.values()) {
            assertScopeChange(scope, BoothReviewChangeType.MODIFIED,
                    scopeSnapshot(scope, true, false),
                    scopeSnapshot(scope, true, true));
        }
    }

    @Test
    void everyScopeReportsRemovedItems() {
        for (BoothReviewChangeScope scope : BoothReviewChangeScope.values()) {
            assertScopeChange(scope, BoothReviewChangeType.REMOVED,
                    scopeSnapshot(scope, true, false),
                    scopeSnapshot(scope, false, false));
        }
    }

    @Test
    void initialSubmissionUsesCurrentCountsWithoutTreatingEverythingAsAdded() {
        BoothReviewSnapshot current = snapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), false);
        BoothReviewChangeSummaryDTO result = service.buildSummary(current, null, 1);

        assertTrue(result.isInitialSubmission());
        assertEquals(0, result.getTotalCount());
        assertEquals(1, result.getCurrentCounts().getProductCount());
        assertEquals(1, result.getCurrentCounts().getProductContentCount());
        assertNull(result.getComparedToRequestId());
        assertEquals(BoothReviewComparisonCompleteness.UNAVAILABLE, result.getComparisonCompleteness());
    }

    @Test
    void legacySnapshotIsPartialAndDoesNotInventMissingProductContents() {
        UUID panoramaId = UUID.randomUUID();
        UUID hotspotId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String legacyJson = """
                {
                  "booth":{"name":"Old"},
                  "panoramas":[{"id":"%s","name":"P","orderIndex":0,"isDefault":true}],
                  "hotspots":[{"id":"%s","name":"H","type":"PRODUCT","sourcePanoramaId":"%s","productId":"%s"}],
                  "productPlacements":[{"itemId":"%s","itemName":"Product","hotspotId":"%s","panoramaId":"%s"}],
                  "mediaPlacements":[]
                }
                """.formatted(panoramaId, hotspotId, panoramaId, productId, productId, hotspotId, panoramaId);
        BoothReviewRequest previous = BoothReviewRequest.builder()
                .id(UUID.randomUUID()).contentSnapshotJson(legacyJson).build();
        BoothReviewSnapshot current = snapshot(
                panoramaId, hotspotId, productId, UUID.randomUUID(), null, UUID.randomUUID(), false);

        BoothReviewChangeSummaryDTO result = service.buildSummary(current, previous, 2);

        assertEquals(BoothReviewComparisonCompleteness.LEGACY_PARTIAL, result.getComparisonCompleteness());
        assertTrue(result.getItems().stream().noneMatch(item ->
                item.getScope() == BoothReviewChangeScope.PRODUCT_CONTENT));
    }

    @Test
    void priorRequestWithoutSnapshotIsUnavailable() {
        BoothReviewRequest previous = BoothReviewRequest.builder().id(UUID.randomUUID()).build();
        BoothReviewChangeSummaryDTO result = service.buildSummary(
                snapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                        UUID.randomUUID(), false),
                previous,
                2);
        assertEquals(BoothReviewComparisonCompleteness.UNAVAILABLE, result.getComparisonCompleteness());
        assertEquals(0, result.getTotalCount());
    }

    @Test
    void legacyFieldsStillDeserialize() {
        BoothReviewChangeSummaryDTO result = service.readSummary("""
                {"initialSubmission":false,"addedCount":0,"modifiedCount":1,"removedCount":0,"totalCount":1,
                 "items":[{"type":"MODIFIED","scope":"BOOTH","fields":["name"]}]}
                """);
        assertEquals(List.of("name"), result.getItems().get(0).getFields());
        assertNull(result.getItems().get(0).getFieldChanges());
    }

    @Test
    void blankJsonReturnsNull() {
        assertNull(service.readSummary("  "));
        assertNull(service.readSnapshot(null));
    }

    @Test
    void serializationFailureMapsToDomainError() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") { });
        BoothReviewDiffService failingService = new BoothReviewDiffService(objectMapper);

        AppException exception = assertThrows(AppException.class, () -> failingService.writeJson(new Object()));

        assertSame(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
    }

    private void assertScopeChange(
            BoothReviewChangeScope scope,
            BoothReviewChangeType type,
            BoothReviewSnapshot previous,
            BoothReviewSnapshot current) {
        BoothReviewRequest previousRequest = BoothReviewRequest.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .contentSnapshotJson(service.writeJson(previous))
                .build();

        BoothReviewChangeSummaryDTO result = service.buildSummary(current, previousRequest, 2);

        assertTrue(result.getItems().stream().anyMatch(item -> item.getScope() == scope && item.getType() == type),
                () -> scope + " should report " + type);
    }

    private BoothReviewSnapshot scopeSnapshot(
            BoothReviewChangeScope scope,
            boolean include,
            boolean changed) {
        BoothReviewSnapshot snapshot = BoothReviewSnapshot.builder()
                .snapshotSchemaVersion(BoothReviewSnapshotFactory.SCHEMA_VERSION)
                .panoramas(List.of())
                .hotspots(List.of())
                .products(List.of())
                .productContents(List.of())
                .mediaAssets(List.of())
                .productPlacements(List.of())
                .mediaPlacements(List.of())
                .build();
        if (!include) {
            return snapshot;
        }

        switch (scope) {
            case BOOTH -> snapshot.setBooth(BoothReviewSnapshot.BoothItem.builder()
                    .id(ITEM_ID).name(changed ? "Changed" : "Original").build());
            case PANORAMA -> snapshot.setPanoramas(List.of(BoothReviewSnapshot.PanoramaItem.builder()
                    .id(ITEM_ID).name(changed ? "Changed" : "Original").orderIndex(0).isDefault(true).build()));
            case HOTSPOT -> snapshot.setHotspots(List.of(BoothReviewSnapshot.HotspotItem.builder()
                    .id(ITEM_ID).name(changed ? "Changed" : "Original").type(HotspotType.INFO)
                    .sourcePanoramaId(PANORAMA_ID).build()));
            case PRODUCT -> snapshot.setProducts(List.of(BoothReviewSnapshot.ProductItem.builder()
                    .id(ITEM_ID).name(changed ? "Changed" : "Original").status(ProductStatus.ACTIVE).build()));
            case PRODUCT_CONTENT -> snapshot.setProductContents(List.of(
                    BoothReviewSnapshot.ProductContentItem.builder()
                            .id(ITEM_ID).productId(PARENT_ID).type(ProductContentType.IMAGE)
                            .url(changed ? "changed.jpg" : "original.jpg").build()));
            case MEDIA_ASSET -> snapshot.setMediaAssets(List.of(BoothReviewSnapshot.MediaAssetItem.builder()
                    .id(ITEM_ID).name(changed ? "Changed" : "Original").type(MediaAssetType.IMAGE).build()));
            case PRODUCT_PLACEMENT -> snapshot.setProductPlacements(List.of(placement(changed)));
            case MEDIA_PLACEMENT -> snapshot.setMediaPlacements(List.of(placement(changed)));
        }
        return snapshot;
    }

    private BoothReviewSnapshot.PlacementItem placement(boolean changed) {
        return BoothReviewSnapshot.PlacementItem.builder()
                .itemId(ITEM_ID)
                .itemName("Item")
                .hotspotId(PARENT_ID)
                .hotspotName("Hotspot")
                .panoramaId(changed ? CHANGED_PANORAMA_ID : PANORAMA_ID)
                .panoramaName("Panorama")
                .build();
    }

    private BoothReviewSnapshot snapshot(
            UUID panoramaId,
            UUID hotspotId,
            UUID productId,
            UUID contentId,
            UUID secondContentId,
            UUID mediaId,
            boolean changed) {
        BoothReviewSnapshot.ProductContentItem content = BoothReviewSnapshot.ProductContentItem.builder()
                .id(contentId).productId(productId).productName("Product").type(ProductContentType.IMAGE)
                .url("content.jpg").mimeType("image/jpeg").fileSize(10L).orderIndex(changed ? 1 : 0).build();
        List<BoothReviewSnapshot.ProductContentItem> contents = secondContentId == null
                ? List.of(content)
                : List.of(content, BoothReviewSnapshot.ProductContentItem.builder()
                        .id(secondContentId).productId(productId).productName("Product")
                        .type(ProductContentType.VIDEO).url("video.mp4").mimeType("video/mp4")
                        .fileSize(20L).orderIndex(1).build());
        return BoothReviewSnapshot.builder()
                .snapshotSchemaVersion(BoothReviewSnapshotFactory.SCHEMA_VERSION)
                .booth(BoothReviewSnapshot.BoothItem.builder().id(UUID.randomUUID())
                        .name(changed ? "New booth" : "Old booth").displayTemplateKey("classic").build())
                .panoramas(List.of(BoothReviewSnapshot.PanoramaItem.builder().id(panoramaId)
                        .name("Panorama").imageUrl(changed ? "new.jpg" : "old.jpg")
                        .orderIndex(0).isDefault(true).build()))
                .hotspots(List.of(BoothReviewSnapshot.HotspotItem.builder().id(hotspotId).name("Hotspot")
                        .type(HotspotType.PRODUCT).sourcePanoramaId(panoramaId).sourcePanoramaName("Panorama")
                        .productId(productId).xPosition(changed ? 2.0 : 1.0).build()))
                .products(List.of(BoothReviewSnapshot.ProductItem.builder().id(productId).name("Product")
                        .sku("SKU").description("Description").thumbnailUrl("thumb.jpg")
                        .price(changed ? BigDecimal.TEN : BigDecimal.ONE).currency("VND")
                        .status(ProductStatus.ACTIVE).build()))
                .productContents(contents)
                .mediaAssets(List.of(BoothReviewSnapshot.MediaAssetItem.builder().id(mediaId).name("Media")
                        .type(MediaAssetType.IMAGE).url(changed ? "new-media.jpg" : "media.jpg")
                        .mimeType("image/jpeg").fileSize(30L).build()))
                .productPlacements(List.of(BoothReviewSnapshot.PlacementItem.builder()
                        .itemId(productId).itemName("Product").hotspotId(hotspotId).hotspotName("Hotspot")
                        .panoramaId(panoramaId).panoramaName("Panorama").build()))
                .mediaPlacements(List.of(BoothReviewSnapshot.PlacementItem.builder()
                        .itemId(mediaId).itemName("Media").hotspotId(changed ? UUID.randomUUID() : hotspotId)
                        .hotspotName("Media hotspot").panoramaId(panoramaId).panoramaName("Panorama").build()))
                .build();
    }
}
