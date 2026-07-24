package com.example.vex360.features.designrequest.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service that creates a baseline DesignDraft for a redesign request by copying
 * the
 * current panoramas, hotspots, and settings of the booth. This provides a
 * starting point
 * (working draft version 0) for the Designer to make changes.
 */
@Service
@RequiredArgsConstructor
public class DesignRequestBaselineService {
    private final PanoramaRepository panoramaRepository;
    private final DesignDraftAssetRepository assetRepository;

    /**
     * Creates the mutable working draft assigned to a Designer. Initial-design
     * requests receive a settings-only draft, while redesign requests also copy
     * the booth's current panoramas, hotspots, and baseline assets.
     *
     * @param request the design request for which to create the working draft
     */
    @Transactional
    public void createWorkingBaseline(DesignRequest request) {
        Booth booth = request.getBooth();
        DesignDraft draft = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .boothName(booth.getName())
                .boothDescription(booth.getDescription())
                .displayTemplateKey(booth.getDisplayTemplateKey())
                .thumbnailAction(DesignDraftFileAction.KEEP)
                .backgroundMusicAction(DesignDraftFileAction.KEEP)
                .build();
        draft.setThumbnailAsset(createBaselineAsset(
                request,
                booth.getThumbnailUrl(),
                booth.getThumbnailPublicId(),
                "thumbnail",
                null,
                DesignDraftAssetType.THUMBNAIL));
        draft.setBackgroundMusicAsset(createBaselineAsset(
                request,
                booth.getBackgroundMusicUrl(),
                booth.getBackgroundMusicPublicId(),
                booth.getBackgroundMusicFileName(),
                booth.getBackgroundMusicFileSize(),
                DesignDraftAssetType.BACKGROUND_MUSIC));

        if (request.getMode() == DesignRequestMode.INITIAL_DESIGN) {
            request.getDrafts().add(draft);
            return;
        }

        List<Panorama> sourcePanoramas = panoramaRepository.findDetailsByBoothId(booth.getId());

        Map<java.util.UUID, String> panoramaKeys = new HashMap<>();
        for (Panorama panorama : sourcePanoramas) {
            panoramaKeys.put(panorama.getId(), panorama.getId().toString());
        }
        for (Panorama panorama : sourcePanoramas) {
            createBaselineAsset(request, panorama.getImageUrl(), panorama.getImageKey(), panorama.getName(),
                    null, DesignDraftAssetType.PANORAMA);
            DesignDraftPanorama draftPanorama = DesignDraftPanorama.builder()
                    .draft(draft)
                    .clientKey(panoramaKeys.get(panorama.getId()))
                    .name(panorama.getName())
                    .imageUrl(panorama.getImageUrl())
                    .imageKey(panorama.getImageKey())
                    .orderIndex(panorama.getOrderIndex())
                    .isDefault(panorama.getIsDefault())
                    .build();
            for (Hotspot hotspot : panorama.getHotspots()) {
                draftPanorama.getHotspots().add(cloneHotspot(draftPanorama, hotspot, panoramaKeys));
            }
            draft.getPanoramas().add(draftPanorama);
        }
        request.getDrafts().add(draft);
    }

    private DesignDraftHotspot cloneHotspot(
            DesignDraftPanorama source,
            Hotspot hotspot,
            Map<java.util.UUID, String> panoramaKeys) {
        DesignDraftHotspot clone = DesignDraftHotspot.builder()
                .sourcePanorama(source)
                .type(hotspot.getType())
                .name(hotspot.getName())
                .targetDraftPanoramaKey(hotspot.getTargetPanorama() == null
                        ? null
                        : panoramaKeys.get(hotspot.getTargetPanorama().getId()))
                .product(hotspot.getProduct())
                .mediaAsset(hotspot.getMediaAsset())
                .infoText(hotspot.getInfoText())
                .xPosition(hotspot.getXPosition())
                .yPosition(hotspot.getYPosition())
                .zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle())
                .scale(hotspot.getScale())
                .zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction())
                .infoContentType(hotspot.getInfoContentType())
                .build();
        clone.setCornerTlX(hotspot.getCornerTlX());
        clone.setCornerTlY(hotspot.getCornerTlY());
        clone.setCornerTlZ(hotspot.getCornerTlZ());
        clone.setCornerTrX(hotspot.getCornerTrX());
        clone.setCornerTrY(hotspot.getCornerTrY());
        clone.setCornerTrZ(hotspot.getCornerTrZ());
        clone.setCornerBlX(hotspot.getCornerBlX());
        clone.setCornerBlY(hotspot.getCornerBlY());
        clone.setCornerBlZ(hotspot.getCornerBlZ());
        clone.setCornerBrX(hotspot.getCornerBrX());
        clone.setCornerBrY(hotspot.getCornerBrY());
        clone.setCornerBrZ(hotspot.getCornerBrZ());
        return clone;
    }

    private DesignDraftAsset createBaselineAsset(
            DesignRequest request,
            String url,
            String publicId,
            String fileName,
            Long fileSize,
            DesignDraftAssetType type) {
        if (url == null || url.isBlank() || publicId == null || publicId.isBlank()) {
            return null;
        }
        return assetRepository.findByDesignRequestIdAndPublicId(request.getId(), publicId)
                .orElseGet(() -> assetRepository.save(DesignDraftAsset.builder()
                        .designRequest(request)
                        .uploadedBy(request.getRequestedBy())
                        .url(url)
                        .publicId(publicId)
                        .fileName(fileName)
                        .mimeType(null)
                        .fileSize(fileSize == null ? 0L : fileSize)
                        .assetType(type)
                        .assetSource(DesignDraftAssetSource.BOOTH_BASELINE)
                        .quotaState(DesignDraftAssetQuotaState.NONE)
                        .build()));
    }
}
