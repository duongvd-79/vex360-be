package com.example.vex360.features.designrequest.services;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

/** Creates an editable revision without changing a submitted draft. */
@Service
public class DesignDraftCloneService {

    @Transactional
    public DesignDraft cloneLatestSubmittedToWorking(DesignRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_REQUEST_INVALID);
        }
        if (request.getDrafts().stream().anyMatch(draft -> draft.getVersionNumber() == 0)) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_ALREADY_EXISTS);
        }

        DesignDraft source = request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() > 0)
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_NOT_FOUND));
        DesignDraft working = cloneDraft(request, source);
        request.getDrafts().add(working);
        return working;
    }

    private DesignDraft cloneDraft(DesignRequest request, DesignDraft source) {

        DesignDraft clone = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .note(source.getNote())
                .boothName(source.getBoothName())
                .boothDescription(source.getBoothDescription())
                .displayTemplateKey(source.getDisplayTemplateKey())
                .thumbnailAction(source.getThumbnailAction())
                .thumbnailAsset(source.getThumbnailAsset())
                .backgroundMusicAction(source.getBackgroundMusicAction())
                .backgroundMusicAsset(source.getBackgroundMusicAsset())
                .build();

        Map<UUID, DesignDraftMediaAsset> clonedMediaBySourceId = new HashMap<>();
        if (source.getMediaAssets() != null) {
            for (DesignDraftMediaAsset sourceMedia : source.getMediaAssets()) {
                DesignDraftMediaAsset cloneMedia = DesignDraftMediaAsset.builder()
                        .draft(clone)
                        .asset(sourceMedia.getAsset())
                        .title(sourceMedia.getTitle())
                        .sortOrder(sourceMedia.getSortOrder())
                        .build();
                clone.getMediaAssets().add(cloneMedia);
                if (sourceMedia.getId() != null) {
                    clonedMediaBySourceId.put(sourceMedia.getId(), cloneMedia);
                }
            }
        }

        for (DesignDraftPanorama sourcePanorama : source.getPanoramas()) {
            DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                    .draft(clone)
                    .clientKey(sourcePanorama.getClientKey())
                    .name(sourcePanorama.getName())
                    .imageUrl(sourcePanorama.getImageUrl())
                    .imageKey(sourcePanorama.getImageKey())
                    .orderIndex(sourcePanorama.getOrderIndex())
                    .isDefault(sourcePanorama.getIsDefault())
                    .build();
            for (DesignDraftHotspot sourceHotspot : sourcePanorama.getHotspots()) {
                panorama.getHotspots().add(cloneHotspot(
                        panorama,
                        sourceHotspot,
                        clonedMediaBySourceId));
            }
            clone.getPanoramas().add(panorama);
        }

        return clone;
    }

    private DesignDraftHotspot cloneHotspot(
            DesignDraftPanorama panorama,
            DesignDraftHotspot source,
            Map<UUID, DesignDraftMediaAsset> clonedMediaBySourceId) {
        DesignDraftMediaAsset sourceMedia = source.getDesignDraftMediaAsset();
        return DesignDraftHotspot.builder()
                .sourcePanorama(panorama)
                .type(source.getType())
                .name(source.getName())
                .targetDraftPanoramaKey(source.getTargetDraftPanoramaKey())
                .product(source.getProduct())
                .mediaAsset(source.getMediaAsset())
                .designDraftMediaAsset(sourceMedia == null || sourceMedia.getId() == null
                        ? null
                        : clonedMediaBySourceId.get(sourceMedia.getId()))
                .infoText(source.getInfoText())
                .xPosition(source.getXPosition())
                .yPosition(source.getYPosition())
                .zPosition(source.getZPosition())
                .iconStyle(source.getIconStyle())
                .scale(source.getScale())
                .zIndex(source.getZIndex())
                .mediaClickAction(source.getMediaClickAction())
                .infoContentType(source.getInfoContentType())
                .cornerTlX(source.getCornerTlX())
                .cornerTlY(source.getCornerTlY())
                .cornerTlZ(source.getCornerTlZ())
                .cornerTrX(source.getCornerTrX())
                .cornerTrY(source.getCornerTrY())
                .cornerTrZ(source.getCornerTrZ())
                .cornerBlX(source.getCornerBlX())
                .cornerBlY(source.getCornerBlY())
                .cornerBlZ(source.getCornerBlZ())
                .cornerBrX(source.getCornerBrX())
                .cornerBrY(source.getCornerBrY())
                .cornerBrZ(source.getCornerBrZ())
                .build();
    }
}
