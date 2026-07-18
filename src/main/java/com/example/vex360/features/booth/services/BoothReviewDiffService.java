package com.example.vex360.features.booth.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.dtos.response.BoothReviewChangeItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewChangeSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentCountsDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewFieldChangeDTO;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewChangeScope;
import com.example.vex360.features.booth.enums.BoothReviewChangeType;
import com.example.vex360.features.booth.enums.BoothReviewComparisonCompleteness;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoothReviewDiffService {
    public static final int COMPARISON_SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;

    public BoothReviewChangeSummaryDTO buildSummary(
            BoothReviewSnapshot current,
            BoothReviewRequest previousRequest,
            int versionNumber) {
        BoothReviewSnapshot previous = previousRequest == null
                ? null
                : readSnapshot(previousRequest.getContentSnapshotJson());
        boolean initial = previousRequest == null;
        BoothReviewComparisonCompleteness completeness = previous == null
                ? BoothReviewComparisonCompleteness.UNAVAILABLE
                : isSchema2(previous)
                        ? BoothReviewComparisonCompleteness.FULL
                        : BoothReviewComparisonCompleteness.LEGACY_PARTIAL;
        List<BoothReviewChangeItemDTO> items = new ArrayList<>();

        if (!initial && previous != null) {
            compare(BoothReviewChangeScope.BOOTH, boothViews(previous), boothViews(current), items);
            compare(BoothReviewChangeScope.PANORAMA, panoramaViews(previous), panoramaViews(current), items);
            compare(BoothReviewChangeScope.HOTSPOT, hotspotViews(previous), hotspotViews(current), items);
            if (isSchema2(previous)) {
                compare(BoothReviewChangeScope.PRODUCT, productViews(previous), productViews(current), items);
                compare(BoothReviewChangeScope.PRODUCT_CONTENT,
                        productContentViews(previous), productContentViews(current), items);
                compare(BoothReviewChangeScope.MEDIA_ASSET, mediaViews(previous), mediaViews(current), items);
            }
            compare(BoothReviewChangeScope.PRODUCT_PLACEMENT,
                    placementViews(previous.getProductPlacements()), placementViews(current.getProductPlacements()), items);
            compare(BoothReviewChangeScope.MEDIA_PLACEMENT,
                    placementViews(previous.getMediaPlacements()), placementViews(current.getMediaPlacements()), items);
        }

        int added = count(items, BoothReviewChangeType.ADDED);
        int modified = count(items, BoothReviewChangeType.MODIFIED);
        int removed = count(items, BoothReviewChangeType.REMOVED);
        return BoothReviewChangeSummaryDTO.builder()
                .comparisonSchemaVersion(COMPARISON_SCHEMA_VERSION)
                .comparisonCompleteness(completeness)
                .initialSubmission(initial)
                .versionNumber(versionNumber)
                .comparedToRequestId(previousRequest == null ? null : previousRequest.getId())
                .comparedToVersionNumber(previousRequest == null ? null : previousRequest.getVersionNumber())
                .currentCounts(counts(current))
                .addedCount(added)
                .modifiedCount(modified)
                .removedCount(removed)
                .totalCount(items.size())
                .items(items)
                .build();
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public BoothReviewChangeSummaryDTO readSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BoothReviewChangeSummaryDTO.class);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public BoothReviewSnapshot readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BoothReviewSnapshot.class);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private boolean isSchema2(BoothReviewSnapshot snapshot) {
        return Integer.valueOf(BoothReviewSnapshotFactory.SCHEMA_VERSION)
                .equals(snapshot.getSnapshotSchemaVersion());
    }

    private BoothReviewContentCountsDTO counts(BoothReviewSnapshot snapshot) {
        return BoothReviewContentCountsDTO.builder()
                .boothCount(snapshot.getBooth() == null ? 0 : 1)
                .panoramaCount(size(snapshot.getPanoramas()))
                .hotspotCount(size(snapshot.getHotspots()))
                .productCount(size(snapshot.getProducts()))
                .productContentCount(size(snapshot.getProductContents()))
                .mediaAssetCount(size(snapshot.getMediaAssets()))
                .productPlacementCount(size(snapshot.getProductPlacements()))
                .mediaPlacementCount(size(snapshot.getMediaPlacements()))
                .build();
    }

    private void compare(
            BoothReviewChangeScope scope,
            List<ResourceView> previous,
            List<ResourceView> current,
            List<BoothReviewChangeItemDTO> items) {
        Map<String, ResourceView> previousByKey = index(previous);
        Map<String, ResourceView> currentByKey = index(current);
        for (ResourceView currentItem : currentByKey.values()) {
            ResourceView previousItem = previousByKey.get(currentItem.key());
            if (previousItem == null) {
                items.add(toChangeItem(BoothReviewChangeType.ADDED, scope, currentItem, null, currentItem));
                continue;
            }
            List<BoothReviewFieldChangeDTO> changes = changedFields(previousItem.fields(), currentItem.fields());
            if (!changes.isEmpty()) {
                items.add(toChangeItem(BoothReviewChangeType.MODIFIED, scope, currentItem, previousItem, currentItem));
            }
        }
        for (ResourceView previousItem : previousByKey.values()) {
            if (!currentByKey.containsKey(previousItem.key())) {
                items.add(toChangeItem(BoothReviewChangeType.REMOVED, scope, previousItem, previousItem, null));
            }
        }
    }

    private BoothReviewChangeItemDTO toChangeItem(
            BoothReviewChangeType type,
            BoothReviewChangeScope scope,
            ResourceView descriptor,
            ResourceView before,
            ResourceView after) {
        Map<String, Object> beforeFields = before == null ? Map.of() : before.fields();
        Map<String, Object> afterFields = after == null ? Map.of() : after.fields();
        List<BoothReviewFieldChangeDTO> fieldChanges = changedFields(beforeFields, afterFields);
        return BoothReviewChangeItemDTO.builder()
                .type(type)
                .scope(scope)
                .itemId(descriptor.itemId())
                .itemName(descriptor.itemName())
                .parentId(descriptor.parentId())
                .parentName(descriptor.parentName())
                .panoramaId(descriptor.panoramaId())
                .panoramaName(descriptor.panoramaName())
                .fields(fieldChanges.stream().map(BoothReviewFieldChangeDTO::getField).toList())
                .fieldChanges(fieldChanges)
                .build();
    }

    private List<BoothReviewFieldChangeDTO> changedFields(
            Map<String, Object> before,
            Map<String, Object> after) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.putAll(before);
        after.forEach(all::putIfAbsent);
        List<BoothReviewFieldChangeDTO> changes = new ArrayList<>();
        for (String field : all.keySet()) {
            Object beforeValue = before.get(field);
            Object afterValue = after.get(field);
            if (!Objects.equals(beforeValue, afterValue)) {
                changes.add(BoothReviewFieldChangeDTO.builder()
                        .field(field).beforeValue(beforeValue).afterValue(afterValue).build());
            }
        }
        return changes;
    }

    private List<ResourceView> boothViews(BoothReviewSnapshot snapshot) {
        BoothReviewSnapshot.BoothItem item = snapshot.getBooth();
        if (item == null) return List.of();
        return List.of(view("booth", item.getId(), item.getName(), null, null, null, null,
                fields("name", item.getName(), "description", item.getDescription(),
                        "thumbnailUrl", item.getThumbnailUrl(), "backgroundMusicUrl", item.getBackgroundMusicUrl(),
                        "displayTemplateKey", item.getDisplayTemplateKey())));
    }

    private List<ResourceView> panoramaViews(BoothReviewSnapshot snapshot) {
        return map(snapshot.getPanoramas(), item -> view(key(item.getId()), item.getId(), item.getName(),
                null, null, item.getId(), item.getName(),
                fields("name", item.getName(), "imageUrl", item.getImageUrl(),
                        "orderIndex", item.getOrderIndex(), "isDefault", item.getIsDefault())));
    }

    private List<ResourceView> hotspotViews(BoothReviewSnapshot snapshot) {
        return map(snapshot.getHotspots(), item -> view(key(item.getId()), item.getId(), item.getName(),
                item.getSourcePanoramaId(), item.getSourcePanoramaName(),
                item.getSourcePanoramaId(), item.getSourcePanoramaName(),
                fields("type", item.getType(), "name", item.getName(),
                        "sourcePanoramaId", item.getSourcePanoramaId(), "targetPanoramaId", item.getTargetPanoramaId(),
                        "productId", item.getProductId(), "mediaAssetId", item.getMediaAssetId(),
                        "infoText", item.getInfoText(), "xPosition", item.getXPosition(),
                        "yPosition", item.getYPosition(), "zPosition", item.getZPosition(),
                        "iconStyle", item.getIconStyle(), "scale", item.getScale(), "zIndex", item.getZIndex(),
                        "mediaClickAction", item.getMediaClickAction(), "infoContentType", item.getInfoContentType(),
                        "cornerTlX", item.getCornerTlX(), "cornerTlY", item.getCornerTlY(), "cornerTlZ", item.getCornerTlZ(),
                        "cornerTrX", item.getCornerTrX(), "cornerTrY", item.getCornerTrY(), "cornerTrZ", item.getCornerTrZ(),
                        "cornerBlX", item.getCornerBlX(), "cornerBlY", item.getCornerBlY(), "cornerBlZ", item.getCornerBlZ(),
                        "cornerBrX", item.getCornerBrX(), "cornerBrY", item.getCornerBrY(), "cornerBrZ", item.getCornerBrZ())));
    }

    private List<ResourceView> productViews(BoothReviewSnapshot snapshot) {
        return map(snapshot.getProducts(), item -> view(key(item.getId()), item.getId(), item.getName(),
                null, null, null, null,
                fields("name", item.getName(), "sku", item.getSku(), "description", item.getDescription(),
                        "thumbnailUrl", item.getThumbnailUrl(), "price", item.getPrice(),
                        "currency", item.getCurrency(), "status", item.getStatus())));
    }

    private List<ResourceView> productContentViews(BoothReviewSnapshot snapshot) {
        return map(snapshot.getProductContents(), item -> view(key(item.getId()), item.getId(),
                item.getType() == null ? null : item.getType().name(), item.getProductId(), item.getProductName(), null, null,
                fields("productId", item.getProductId(), "type", item.getType(), "url", item.getUrl(),
                        "mimeType", item.getMimeType(), "fileSize", item.getFileSize(), "orderIndex", item.getOrderIndex())));
    }

    private List<ResourceView> mediaViews(BoothReviewSnapshot snapshot) {
        return map(snapshot.getMediaAssets(), item -> view(key(item.getId()), item.getId(), item.getName(),
                null, null, null, null,
                fields("name", item.getName(), "type", item.getType(), "url", item.getUrl(),
                        "mimeType", item.getMimeType(), "fileSize", item.getFileSize())));
    }

    private List<ResourceView> placementViews(List<BoothReviewSnapshot.PlacementItem> placements) {
        return map(placements, item -> view(key(item.getHotspotId()) + ":" + key(item.getItemId()),
                item.getItemId(), item.getItemName(), item.getHotspotId(), item.getHotspotName(),
                item.getPanoramaId(), item.getPanoramaName(),
                fields("resourceId", item.getItemId(), "hotspotId", item.getHotspotId(),
                        "panoramaId", item.getPanoramaId())));
    }

    private ResourceView view(String key, UUID itemId, String itemName, UUID parentId, String parentName,
            UUID panoramaId, String panoramaName, Map<String, Object> fields) {
        return new ResourceView(key, itemId, itemName, parentId, parentName, panoramaId, panoramaName, fields);
    }

    private Map<String, Object> fields(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }

    private <T> List<ResourceView> map(List<T> values, Function<T, ResourceView> mapper) {
        return safe(values).stream().map(mapper).toList();
    }

    private Map<String, ResourceView> index(List<ResourceView> values) {
        Map<String, ResourceView> result = new LinkedHashMap<>();
        for (ResourceView value : values) {
            if (value.key() != null) result.put(value.key(), value);
        }
        return result;
    }

    private String key(UUID id) {
        return id == null ? null : id.toString();
    }

    private int count(List<BoothReviewChangeItemDTO> items, BoothReviewChangeType type) {
        return (int) items.stream().filter(item -> item.getType() == type).count();
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ResourceView(
            String key,
            UUID itemId,
            String itemName,
            UUID parentId,
            String parentName,
            UUID panoramaId,
            String panoramaName,
            Map<String, Object> fields) {
    }
}
