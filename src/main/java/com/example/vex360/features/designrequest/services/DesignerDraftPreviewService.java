package com.example.vex360.features.designrequest.services;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotPanoramaSummaryDTO;
import com.example.vex360.features.booth.dtos.response.HotspotProductSummaryDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPanoramaResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPreviewBoothResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPreviewResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftSettingsResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignDraftPreviewSource;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignerDraftPreviewService {
    private static final int WORKING_VERSION = 0;

    private final DesignerWorkspaceService workspaceService;
    private final DesignDraftRepository draftRepository;
    private final BoothMapper boothMapper;
    private final DesignDraftBenefitGuardService benefitGuardService;

    @Transactional(readOnly = true)
    public DesignDraftPreviewResponseDTO getPreview(
            User designer,
            UUID requestId,
            DesignDraftPreviewSource requestedSource) {
        DesignRequest request = workspaceService.getAssignedRequest(designer, requestId);
        DesignDraft working = draftRepository
                .findByDesignRequestIdAndVersionNumber(requestId, WORKING_VERSION)
                .orElse(null);
        DesignDraft latestSubmitted = draftRepository
                .findFirstByDesignRequestIdAndVersionNumberGreaterThanOrderByVersionNumberDesc(
                        requestId,
                        WORKING_VERSION)
                .orElse(null);
        DesignDraftPreviewSource source = resolveSource(requestedSource, working, latestSubmitted);
        DesignDraft selectedDraft = switch (source) {
            case WORKING_DRAFT -> requireDraft(working);
            case LATEST_SUBMITTED -> requireDraft(latestSubmitted);
            case CURRENT_BOOTH -> null;
        };

        return new DesignDraftPreviewResponseDTO(
                request.getId(),
                request.getMode(),
                request.getStatus(),
                source,
                isEditable(request, source, selectedDraft),
                selectedDraft == null ? null : selectedDraft.getId(),
                selectedDraft == null ? null : selectedDraft.getVersionNumber(),
                selectedDraft == null
                        ? benefitGuardService.getBaselineUsageResponse(request)
                        : benefitGuardService.getUsageResponse(request, selectedDraft),
                selectedDraft == null
                        ? toCurrentBoothResponse(request.getBooth())
                        : toDraftBoothResponse(request, selectedDraft));
    }

    DesignDraftSettingsResponseDTO toSettingsResponse(DesignRequest request, DesignDraft draft) {
        Booth booth = request.getBooth();
        DesignDraftAsset thumbnailAsset = draft.getThumbnailAsset();
        DesignDraftAsset musicAsset = draft.getBackgroundMusicAsset();
        return new DesignDraftSettingsResponseDTO(
                draft.getNote(),
                draft.getBoothName(),
                draft.getBoothDescription(),
                draft.getDisplayTemplateKey(),
                draft.getThumbnailAction(),
                thumbnailAsset == null ? null : thumbnailAsset.getId(),
                resolveUrl(draft.getThumbnailAction(), thumbnailAsset, booth.getThumbnailUrl()),
                draft.getBackgroundMusicAction(),
                musicAsset == null ? null : musicAsset.getId(),
                resolveUrl(draft.getBackgroundMusicAction(), musicAsset, booth.getBackgroundMusicUrl()),
                resolveMusicFileName(draft, booth),
                resolveMusicFileSize(draft, booth));
    }

    DesignDraftPanoramaResponseDTO toPanoramaResponse(DesignDraftPanorama panorama) {
        Map<String, DesignDraftPanorama> panoramasByKey = panorama.getDraft().getPanoramas().stream()
                .filter(item -> item.getClientKey() != null)
                .collect(Collectors.toMap(
                        DesignDraftPanorama::getClientKey,
                        Function.identity(),
                        (left, right) -> left));
        return toPanoramaResponse(panorama, panoramasByKey);
    }

    HotspotResponseDTO toHotspotResponse(DesignDraftHotspot hotspot) {
        Map<String, DesignDraftPanorama> panoramasByKey = hotspot.getSourcePanorama().getDraft().getPanoramas().stream()
                .filter(item -> item.getClientKey() != null)
                .collect(Collectors.toMap(
                        DesignDraftPanorama::getClientKey,
                        Function.identity(),
                        (left, right) -> left));
        return toHotspotResponse(hotspot, panoramasByKey);
    }

    private DesignDraftPreviewBoothResponseDTO toDraftBoothResponse(DesignRequest request, DesignDraft draft) {
        DesignDraftSettingsResponseDTO settings = toSettingsResponse(request, draft);
        Map<String, DesignDraftPanorama> panoramasByKey = draft.getPanoramas().stream()
                .filter(item -> item.getClientKey() != null)
                .collect(Collectors.toMap(
                        DesignDraftPanorama::getClientKey,
                        Function.identity(),
                        (left, right) -> left));
        List<DesignDraftPanoramaResponseDTO> panoramas = draft.getPanoramas().stream()
                .sorted(Comparator.comparing(
                        DesignDraftPanorama::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(panorama -> toPanoramaResponse(panorama, panoramasByKey))
                .toList();
        return new DesignDraftPreviewBoothResponseDTO(
                settings.getName(),
                settings.getDescription(),
                settings.getThumbnailUrl(),
                settings.getBackgroundMusicUrl(),
                settings.getBackgroundMusicFileName(),
                settings.getBackgroundMusicFileSize(),
                settings.getDisplayTemplateKey(),
                panoramas);
    }

    private DesignDraftPreviewBoothResponseDTO toCurrentBoothResponse(Booth booth) {
        BoothResponseDTO response = boothMapper.toBoothResponseDTO(booth);
        List<DesignDraftPanoramaResponseDTO> panoramas = response.getPanoramas().stream()
                .map(this::toCurrentPanoramaResponse)
                .toList();
        return new DesignDraftPreviewBoothResponseDTO(
                response.getName(),
                response.getDescription(),
                response.getThumbnailUrl(),
                response.getBackgroundMusicUrl(),
                response.getBackgroundMusicFileName(),
                response.getBackgroundMusicFileSize(),
                response.getDisplayTemplateKey(),
                panoramas);
    }

    private DesignDraftPanoramaResponseDTO toCurrentPanoramaResponse(PanoramaResponseDTO panorama) {
        return new DesignDraftPanoramaResponseDTO(
                panorama.getId(),
                panorama.getId() == null ? null : panorama.getId().toString(),
                panorama.getName(),
                panorama.getImageUrl(),
                panorama.getImageKey(),
                panorama.getOrderIndex(),
                panorama.getIsDefault(),
                panorama.getHotspots());
    }

    private DesignDraftPanoramaResponseDTO toPanoramaResponse(
            DesignDraftPanorama panorama,
            Map<String, DesignDraftPanorama> panoramasByKey) {
        List<HotspotResponseDTO> hotspots = panorama.getHotspots().stream()
                .sorted(Comparator.comparing(
                        DesignDraftHotspot::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(hotspot -> toHotspotResponse(hotspot, panoramasByKey))
                .toList();
        return new DesignDraftPanoramaResponseDTO(
                panorama.getId(),
                panorama.getClientKey(),
                panorama.getName(),
                panorama.getImageUrl(),
                panorama.getImageKey(),
                panorama.getOrderIndex(),
                panorama.getIsDefault(),
                hotspots);
    }

    private HotspotResponseDTO toHotspotResponse(
            DesignDraftHotspot hotspot,
            Map<String, DesignDraftPanorama> panoramasByKey) {
        DesignDraftPanorama source = hotspot.getSourcePanorama();
        DesignDraftPanorama target = panoramasByKey.get(hotspot.getTargetDraftPanoramaKey());
        return new HotspotResponseDTO(
                hotspot.getId(),
                hotspot.getType(),
                hotspot.getName(),
                source == null ? null : source.getId(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                target == null ? null : new HotspotPanoramaSummaryDTO(target.getId(), target.getName()),
                toProductSummary(hotspot.getProduct()),
                boothMapper.toMediaAssetResponseDTO(hotspot.getMediaAsset()),
                hotspot.getInfoText(),
                hotspot.getXPosition(),
                hotspot.getYPosition(),
                hotspot.getZPosition(),
                hotspot.getIconStyle(),
                hotspot.getScale(),
                hotspot.getZIndex(),
                hotspot.getMediaClickAction(),
                hotspot.getInfoContentType(),
                hotspot.getCorners());
    }

    private HotspotProductSummaryDTO toProductSummary(Product product) {
        if (product == null) {
            return null;
        }
        return new HotspotProductSummaryDTO(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getThumbnailUrl(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus(),
                null,
                null);
    }

    private DesignDraftPreviewSource resolveSource(
            DesignDraftPreviewSource requestedSource,
            DesignDraft working,
            DesignDraft latestSubmitted) {
        if (requestedSource != null) {
            return requestedSource;
        }
        if (working != null) {
            return DesignDraftPreviewSource.WORKING_DRAFT;
        }
        if (latestSubmitted != null) {
            return DesignDraftPreviewSource.LATEST_SUBMITTED;
        }
        return DesignDraftPreviewSource.CURRENT_BOOTH;
    }

    private DesignDraft requireDraft(DesignDraft draft) {
        if (draft == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return draft;
    }

    private boolean isEditable(
            DesignRequest request,
            DesignDraftPreviewSource source,
            DesignDraft draft) {
        return source == DesignDraftPreviewSource.WORKING_DRAFT
                && draft != null
                && Objects.equals(draft.getVersionNumber(), WORKING_VERSION)
                && (request.getStatus() == DesignRequestStatus.ASSIGNED
                        || request.getStatus() == DesignRequestStatus.REVISION_REQUESTED)
                && request.getCancellationStatus() != DesignRequestCancellationStatus.REQUESTED;
    }

    private String resolveUrl(DesignDraftFileAction action, DesignDraftAsset asset, String boothUrl) {
        DesignDraftFileAction effectiveAction = action == null ? DesignDraftFileAction.KEEP : action;
        return switch (effectiveAction) {
            case KEEP -> asset == null ? boothUrl : asset.getUrl();
            case REPLACE -> asset == null ? null : asset.getUrl();
            case CLEAR -> null;
        };
    }

    private String resolveMusicFileName(DesignDraft draft, Booth booth) {
        DesignDraftFileAction action = draft.getBackgroundMusicAction() == null
                ? DesignDraftFileAction.KEEP
                : draft.getBackgroundMusicAction();
        if (action == DesignDraftFileAction.KEEP) {
            return draft.getBackgroundMusicAsset() == null
                    ? booth.getBackgroundMusicFileName()
                    : draft.getBackgroundMusicAsset().getFileName();
        }
        return action == DesignDraftFileAction.REPLACE && draft.getBackgroundMusicAsset() != null
                ? draft.getBackgroundMusicAsset().getFileName()
                : null;
    }

    private Long resolveMusicFileSize(DesignDraft draft, Booth booth) {
        DesignDraftFileAction action = draft.getBackgroundMusicAction() == null
                ? DesignDraftFileAction.KEEP
                : draft.getBackgroundMusicAction();
        if (action == DesignDraftFileAction.KEEP) {
            return draft.getBackgroundMusicAsset() == null
                    ? booth.getBackgroundMusicFileSize()
                    : draft.getBackgroundMusicAsset().getFileSize();
        }
        return action == DesignDraftFileAction.REPLACE && draft.getBackgroundMusicAsset() != null
                ? draft.getBackgroundMusicAsset().getFileSize()
                : null;
    }
}
