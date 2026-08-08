package com.example.vex360.features.designrequest.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@Service
public class DesignDraftGraphValidator {
    public void validateWorkingGraph(DesignRequest request, DesignDraft draft) {
        validateGraph(request, draft, false);
    }

    public void validateForSubmission(DesignRequest request, DesignDraft draft) {
        validateGraph(request, draft, true);
    }

    public void validateGraph(DesignRequest request, DesignDraft draft, boolean requireSubmittable) {
        if (request == null || draft == null || draft.getDesignRequest() == null
                || !sameEntity(request.getId(), draft.getDesignRequest().getId(), request, draft.getDesignRequest())) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_GRAPH_INVALID);
        }

        List<DesignDraftPanorama> panoramas = draft.getPanoramas();
        if (panoramas == null || requireSubmittable && panoramas.isEmpty()) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID);
        }
        if (panoramas.isEmpty()) {
            return;
        }

        Set<String> panoramaKeys = new HashSet<>();
        Set<Integer> orderIndexes = new HashSet<>();
        int defaultCount = 0;
        for (DesignDraftPanorama panorama : panoramas) {
            if (panorama == null
                    || !isText(panorama.getClientKey())
                    || !panoramaKeys.add(panorama.getClientKey().trim())
                    || !isText(panorama.getName())
                    || !isText(panorama.getImageUrl())
                    || !isText(panorama.getImageKey())) {
                invalidDraft(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID);
            }
            if (panorama.getOrderIndex() == null
                    || panorama.getOrderIndex() < 0
                    || !orderIndexes.add(panorama.getOrderIndex())) {
                invalidDraft(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID);
            }
            if (Boolean.TRUE.equals(panorama.getIsDefault())) {
                defaultCount++;
            }
        }
        if (defaultCount != 1) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_DEFAULT_PANORAMA_INVALID);
        }
        for (int orderIndex = 0; orderIndex < panoramas.size(); orderIndex++) {
            if (!orderIndexes.contains(orderIndex)) {
                invalidDraft(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID);
            }
        }

        Set<UUID> allowedProductIds = allowedProductIds(request);
        Set<UUID> allowedMediaAssetIds = allowedMediaAssetIds(request);
        for (DesignDraftPanorama panorama : panoramas) {
            if (panorama.getHotspots() == null) {
                continue;
            }
            for (DesignDraftHotspot hotspot : panorama.getHotspots()) {
                validateHotspot(
                        request,
                        panorama,
                        hotspot,
                        panoramaKeys,
                        allowedProductIds,
                        allowedMediaAssetIds);
            }
        }
    }

    private void validateHotspot(
            DesignRequest request,
            DesignDraftPanorama panorama,
            DesignDraftHotspot hotspot,
            Set<String> panoramaKeys,
            Set<UUID> allowedProductIds,
            Set<UUID> allowedMediaAssetIds) {
        if (hotspot == null
                || hotspot.getSourcePanorama() == null
                || !sameEntity(
                        panorama.getId(),
                        hotspot.getSourcePanorama().getId(),
                        panorama,
                        hotspot.getSourcePanorama())
                || hotspot.getType() == null
                || !isText(hotspot.getName())
                || !isFinite(hotspot.getXPosition())
                || !isFinite(hotspot.getYPosition())
                || !isFinite(hotspot.getZPosition())
                || hotspot.getScale() != null && !Double.isFinite(hotspot.getScale())) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }

        validateCorners(hotspot);
        switch (hotspot.getType()) {
            case NAV -> validateNav(hotspot, panoramaKeys);
            case PRODUCT -> validateProductHotspot(request, hotspot, allowedProductIds);
            case INFO -> validateInfoHotspot(request, hotspot, allowedProductIds, allowedMediaAssetIds);
            case MEDIA -> validateMediaHotspot(request, hotspot, allowedMediaAssetIds);
            default -> invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
    }

    private void validateNav(DesignDraftHotspot hotspot, Set<String> panoramaKeys) {
        String targetKey = normalized(hotspot.getTargetDraftPanoramaKey());
        if (targetKey == null
                || !panoramaKeys.contains(targetKey)
                || hotspot.getProduct() != null
                || hotspot.getMediaAsset() != null
                || hotspot.getDesignDraftMediaAsset() != null
                || isText(hotspot.getInfoText())
                || hotspot.getInfoContentType() != null
                || hotspot.getMediaClickAction() != null
                || hasAnyCorner(hotspot)) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_REFERENCE_INVALID);
        }
    }

    private void validateProductHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            Set<UUID> allowedProductIds) {
        if (isText(hotspot.getTargetDraftPanoramaKey())
                || hotspot.getMediaAsset() != null
                || hotspot.getDesignDraftMediaAsset() != null
                || isText(hotspot.getInfoText())
                || hotspot.getInfoContentType() != null
                || hotspot.getMediaClickAction() != null) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
        validateProduct(request, hotspot.getProduct(), allowedProductIds);
    }

    private void validateInfoHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            Set<UUID> allowedProductIds,
            Set<UUID> allowedMediaAssetIds) {
        if (isText(hotspot.getTargetDraftPanoramaKey())
                || hotspot.getMediaClickAction() != null
                || hasAnyCorner(hotspot)
                || hotspot.getInfoContentType() == null) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }

        HotspotInfoContentType contentType = hotspot.getInfoContentType();
        switch (contentType) {
            case NONE -> {
                if (isText(hotspot.getInfoText()) || hotspot.getProduct() != null
                        || hotspot.getMediaAsset() != null || hotspot.getDesignDraftMediaAsset() != null) {
                    invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
            }
            case TEXT -> {
                if (!isText(hotspot.getInfoText()) || hotspot.getProduct() != null
                        || hotspot.getMediaAsset() != null || hotspot.getDesignDraftMediaAsset() != null) {
                    invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
            }
            case IMAGE -> {
                if (isText(hotspot.getInfoText()) || hotspot.getProduct() != null) {
                    invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
                validateMediaReference(request, hotspot, MediaAssetType.IMAGE, allowedMediaAssetIds);
            }
            case VIDEO -> {
                if (isText(hotspot.getInfoText()) || hotspot.getProduct() != null) {
                    invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
                validateMediaReference(request, hotspot, MediaAssetType.VIDEO, allowedMediaAssetIds);
            }
            case PRODUCT -> {
                if (isText(hotspot.getInfoText()) || hotspot.getMediaAsset() != null
                        || hotspot.getDesignDraftMediaAsset() != null) {
                    invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
                validateProduct(request, hotspot.getProduct(), allowedProductIds);
            }
            default -> invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
    }

    private void validateMediaHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            Set<UUID> allowedMediaAssetIds) {
        if (isText(hotspot.getTargetDraftPanoramaKey())
                || hotspot.getProduct() != null
                || isText(hotspot.getInfoText())
                || hotspot.getInfoContentType() != null
                || hotspot.getMediaClickAction() == null) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
        validateMediaReference(request, hotspot, null, allowedMediaAssetIds);
    }

    private void validateProduct(DesignRequest request, Product product, Set<UUID> allowedProductIds) {
        if (product == null
                || product.getId() == null
                || !allowedProductIds.contains(product.getId())
                || product.getStatus() != ProductStatus.ACTIVE
                || !sameCompany(request.getCompany(), product.getCompany())) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_PRODUCT_REFERENCE_INVALID);
        }
    }

    private void validateMedia(
            DesignRequest request,
            MediaAsset mediaAsset,
            MediaAssetType expectedType,
            Set<UUID> allowedMediaAssetIds) {
        if (mediaAsset == null || mediaAsset.getId() == null) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        if (!allowedMediaAssetIds.contains(mediaAsset.getId())) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        if (mediaAsset.getType() == null
                || !sameCompany(request.getCompany(), mediaAsset.getCompany())
                || expectedType != null && mediaAsset.getType() != expectedType) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
    }

    private void validateMediaReference(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            MediaAssetType expectedType,
            Set<UUID> allowedMediaAssetIds) {
        boolean official = hotspot.getMediaAsset() != null;
        boolean staging = hotspot.getDesignDraftMediaAsset() != null;
        if (official == staging) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        if (official) {
            validateMedia(request, hotspot.getMediaAsset(), expectedType, allowedMediaAssetIds);
            return;
        }
        DesignDraftMediaAsset media = hotspot.getDesignDraftMediaAsset();
        if (media.getId() == null
                || media.getDraft() == null
                || hotspot.getSourcePanorama() == null
                || hotspot.getSourcePanorama().getDraft() == null
                || !sameEntity(
                        media.getDraft().getId(),
                        hotspot.getSourcePanorama().getDraft().getId(),
                        media.getDraft(),
                        hotspot.getSourcePanorama().getDraft())
                || media.getAsset() == null
                || media.getAsset()
                        .getAssetType() != com.example.vex360.features.designrequest.enums.DesignDraftAssetType.MEDIA_ATTACHMENT
                || expectedType != null && stagingMediaType(media) != expectedType) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
    }

    private MediaAssetType stagingMediaType(DesignDraftMediaAsset media) {
        String mimeType = media.getAsset().getMimeType();
        if ("video/mp4".equalsIgnoreCase(mimeType)) {
            return MediaAssetType.VIDEO;
        }
        if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType)) {
            return MediaAssetType.IMAGE;
        }
        invalidDraft(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        return null;
    }

    private Set<UUID> allowedProductIds(DesignRequest request) {
        Set<UUID> productIds = new HashSet<>();
        if (request.getProducts() == null) {
            return productIds;
        }
        request.getProducts().forEach(item -> {
            if (item != null && item.getProduct() != null && item.getProduct().getId() != null) {
                productIds.add(item.getProduct().getId());
            }
        });
        return productIds;
    }

    private Set<UUID> allowedMediaAssetIds(DesignRequest request) {
        Set<UUID> mediaAssetIds = new HashSet<>();
        if (request.getMediaAssets() == null) {
            return mediaAssetIds;
        }
        request.getMediaAssets().forEach(item -> {
            if (item != null && item.getMediaAsset() != null && item.getMediaAsset().getId() != null) {
                mediaAssetIds.add(item.getMediaAsset().getId());
            }
        });
        return mediaAssetIds;
    }

    private void validateCorners(DesignDraftHotspot hotspot) {
        boolean hasCorners = hasAnyCorner(hotspot);
        if (!hasCorners) {
            return;
        }
        if (hotspot.getType() != HotspotType.MEDIA && hotspot.getType() != HotspotType.PRODUCT) {
            invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
        Double[] values = cornerValues(hotspot);
        for (Double value : values) {
            if (!isFinite(value)) {
                invalidDraft(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
            }
        }
    }

    private boolean hasAnyCorner(DesignDraftHotspot hotspot) {
        for (Double value : cornerValues(hotspot)) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private Double[] cornerValues(DesignDraftHotspot hotspot) {
        return new Double[] {
                hotspot.getCornerTlX(), hotspot.getCornerTlY(), hotspot.getCornerTlZ(),
                hotspot.getCornerTrX(), hotspot.getCornerTrY(), hotspot.getCornerTrZ(),
                hotspot.getCornerBlX(), hotspot.getCornerBlY(), hotspot.getCornerBlZ(),
                hotspot.getCornerBrX(), hotspot.getCornerBrY(), hotspot.getCornerBrZ()
        };
    }

    private boolean sameCompany(Company expected, Company actual) {
        return expected != null
                && actual != null
                && expected.getId() != null
                && expected.getId().equals(actual.getId());
    }

    private boolean sameEntity(UUID firstId, UUID secondId, Object first, Object second) {
        return firstId != null && firstId.equals(secondId) || first == second;
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private boolean isText(String value) {
        return normalized(value) != null;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void invalidDraft(ErrorCode errorCode) {
        throw new AppException(errorCode);
    }
}
