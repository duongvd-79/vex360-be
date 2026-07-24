package com.example.vex360.features.designrequest.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.ReorderDesignDraftPanoramasRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftSettingsRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPanoramaResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftSettingsResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignerDraftEditorService {
    private static final int WORKING_VERSION = 0;

    private final DesignRequestRepository requestRepository;
    private final DesignDraftRepository draftRepository;
    private final DesignDraftAssetRepository draftAssetRepository;
    private final DesignDraftAssetService assetService;
    private final DesignRequestProductService requestProductService;
    private final ProductService productService;
    private final BoothDesignService boothDesignService;
    private final DesignDraftBenefitGuardService benefitGuardService;
    private final DesignDraftGraphValidator graphValidator;
    private final DesignerDraftPreviewService previewService;

    @Transactional
    public DesignDraftSettingsResponseDTO updateSettings(
            User designer,
            UUID requestId,
            UpdateDesignDraftSettingsRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId);
        if (update == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraft draft = context.draft();
        draft.setNote(trimToNull(update.getNote()));
        draft.setBoothName(requireText(update.getName()));
        draft.setBoothDescription(trimToNull(update.getDescription()));
        draft.setDisplayTemplateKey(requireText(update.getDisplayTemplateKey()));
        applyThumbnailSettings(context.request(), draft, update);
        applyMusicSettings(context.request(), draft, update);
        graphValidator.validateWorkingGraph(context.request(), draft);
        DesignDraft saved = draftRepository.saveAndFlush(draft);
        return previewService.toSettingsResponse(context.request(), saved);
    }

    @Transactional
    public DesignDraftPanoramaResponseDTO createPanorama(
            User designer,
            UUID requestId,
            CreateDesignDraftPanoramaRequest create) {
        EditableDraft context = getEditableDraft(designer, requestId);
        if (create == null || create.getPanoramaAssetId() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraft draft = context.draft();
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        DesignDraftAsset asset = assetService.requireDraftAsset(
                context.request(),
                create.getPanoramaAssetId(),
                DesignDraftAssetType.PANORAMA);
        int insertionIndex = resolveInsertionIndex(create.getOrderIndex(), draft.getPanoramas().size());
        for (DesignDraftPanorama existing : draft.getPanoramas()) {
            if (existing.getOrderIndex() != null && existing.getOrderIndex() >= insertionIndex) {
                existing.setOrderIndex(existing.getOrderIndex() + 1);
            }
        }
        DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                .draft(draft)
                .clientKey(UUID.randomUUID().toString())
                .name(requireText(create.getName()))
                .imageUrl(asset.getUrl())
                .imageKey(asset.getPublicId())
                .orderIndex(insertionIndex)
                .isDefault(draft.getPanoramas().isEmpty() || Boolean.TRUE.equals(create.getIsDefault()))
                .build();
        if (Boolean.TRUE.equals(panorama.getIsDefault())) {
            clearDefault(draft, null);
        }
        draft.getPanoramas().add(panorama);
        normalizePanoramas(draft);
        graphValidator.validateWorkingGraph(context.request(), draft);
        benefitGuardService.assertMutationAllowed(context.request(), beforeUsage, draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toPanoramaResponse(panorama);
    }

    @Transactional
    public DesignDraftPanoramaResponseDTO updatePanorama(
            User designer,
            UUID requestId,
            UUID panoramaId,
            UpdateDesignDraftPanoramaRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId);
        if (update == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraft draft = context.draft();
        DesignDraftPanorama panorama = getPanorama(draft, panoramaId);
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        if (update.getName() != null) {
            panorama.setName(requireText(update.getName()));
        }
        if (update.getPanoramaAssetId() != null) {
            DesignDraftAsset asset = assetService.requireDraftAsset(
                    context.request(),
                    update.getPanoramaAssetId(),
                    DesignDraftAssetType.PANORAMA);
            panorama.setImageUrl(asset.getUrl());
            panorama.setImageKey(asset.getPublicId());
        }
        if (Boolean.TRUE.equals(update.getIsDefault())) {
            clearDefault(draft, panorama);
            panorama.setIsDefault(true);
        } else if (Boolean.FALSE.equals(update.getIsDefault())) {
            panorama.setIsDefault(false);
        }
        normalizePanoramas(draft);
        graphValidator.validateWorkingGraph(context.request(), draft);
        benefitGuardService.assertMutationAllowed(context.request(), beforeUsage, draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toPanoramaResponse(panorama);
    }

    @Transactional
    public List<DesignDraftPanoramaResponseDTO> reorderPanoramas(
            User designer,
            UUID requestId,
            ReorderDesignDraftPanoramasRequest reorder) {
        EditableDraft context = getEditableDraft(designer, requestId);
        if (reorder == null || reorder.getPanoramaIds() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraft draft = context.draft();
        List<UUID> requestedIds = reorder.getPanoramaIds();
        Set<UUID> uniqueIds = new HashSet<>(requestedIds);
        Set<UUID> currentIds = draft.getPanoramas().stream()
                .map(DesignDraftPanorama::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (uniqueIds.size() != requestedIds.size() || !uniqueIds.equals(currentIds)) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        for (int index = 0; index < requestedIds.size(); index++) {
            getPanorama(draft, requestedIds.get(index)).setOrderIndex(index);
        }
        normalizePanoramas(draft);
        graphValidator.validateWorkingGraph(context.request(), draft);
        draftRepository.saveAndFlush(draft);
        return draft.getPanoramas().stream()
                .sorted(Comparator.comparing(DesignDraftPanorama::getOrderIndex))
                .map(previewService::toPanoramaResponse)
                .toList();
    }

    @Transactional
    public DesignDraftPanoramaResponseDTO deletePanorama(
            User designer,
            UUID requestId,
            UUID panoramaId) {
        EditableDraft context = getEditableDraft(designer, requestId);
        DesignDraft draft = context.draft();
        DesignDraftPanorama panorama = getPanorama(draft, panoramaId);
        DesignDraftPanoramaResponseDTO response = previewService.toPanoramaResponse(panorama);
        String deletedKey = panorama.getClientKey();
        for (DesignDraftPanorama existing : draft.getPanoramas()) {
            existing.getHotspots().removeIf(hotspot -> Objects.equals(
                    hotspot.getTargetDraftPanoramaKey(),
                    deletedKey));
        }
        draft.getPanoramas().remove(panorama);
        normalizePanoramas(draft);
        graphValidator.validateWorkingGraph(context.request(), draft);
        draftRepository.saveAndFlush(draft);
        return response;
    }

    @Transactional
    public HotspotResponseDTO createHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            UpsertHotspotRequest create) {
        EditableDraft context = getEditableDraft(designer, requestId);
        DesignDraft draft = context.draft();
        DesignDraftPanorama source = getPanorama(draft, panoramaId);
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        DesignDraftHotspot hotspot = DesignDraftHotspot.builder()
                .sourcePanorama(source)
                .build();
        applyHotspot(context.request(), draft, hotspot, create);
        source.getHotspots().add(hotspot);
        graphValidator.validateWorkingGraph(context.request(), draft);
        benefitGuardService.assertMutationAllowed(context.request(), beforeUsage, draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toHotspotResponse(hotspot);
    }

    @Transactional
    public HotspotResponseDTO updateHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            UUID hotspotId,
            UpsertHotspotRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId);
        DesignDraft draft = context.draft();
        DesignDraftPanorama source = getPanorama(draft, panoramaId);
        DesignDraftHotspot hotspot = getHotspot(source, hotspotId);
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        applyHotspot(context.request(), draft, hotspot, update);
        graphValidator.validateWorkingGraph(context.request(), draft);
        benefitGuardService.assertMutationAllowed(context.request(), beforeUsage, draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toHotspotResponse(hotspot);
    }

    @Transactional
    public HotspotResponseDTO deleteHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            UUID hotspotId) {
        EditableDraft context = getEditableDraft(designer, requestId);
        DesignDraftPanorama source = getPanorama(context.draft(), panoramaId);
        DesignDraftHotspot hotspot = getHotspot(source, hotspotId);
        HotspotResponseDTO response = previewService.toHotspotResponse(hotspot);
        source.getHotspots().remove(hotspot);
        graphValidator.validateWorkingGraph(context.request(), context.draft());
        draftRepository.saveAndFlush(context.draft());
        return response;
    }

    private EditableDraft getEditableDraft(User designer, UUID requestId) {
        if (designer == null || designer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        DesignRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
        if (request.getAssignedDesigner() == null
                || !designer.getId().equals(request.getAssignedDesigner().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (request.getStatus() != DesignRequestStatus.ASSIGNED
                && request.getStatus() != DesignRequestStatus.REVISION_REQUESTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        if (request.getCancellationStatus() == DesignRequestCancellationStatus.REQUESTED) {
            throw new AppException(ErrorCode.DESIGN_CANCELLATION_PENDING);
        }
        DesignDraft draft = draftRepository.findByDesignRequestIdAndVersionNumber(requestId, WORKING_VERSION)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        return new EditableDraft(request, draft);
    }

    private void applyThumbnailSettings(
            DesignRequest request,
            DesignDraft draft,
            UpdateDesignDraftSettingsRequest update) {
        DesignDraftFileAction action = update.getThumbnailAction() == null
                ? DesignDraftFileAction.KEEP
                : update.getThumbnailAction();
        draft.setThumbnailAction(action);
        draft.setThumbnailAsset(switch (action) {
            case REPLACE -> requireAsset(request, update.getThumbnailAssetId(), DesignDraftAssetType.THUMBNAIL);
            case KEEP -> findBaselineAsset(
                    request,
                    request.getBooth().getThumbnailPublicId(),
                    DesignDraftAssetType.THUMBNAIL);
            case CLEAR -> null;
        });
    }

    private void applyMusicSettings(
            DesignRequest request,
            DesignDraft draft,
            UpdateDesignDraftSettingsRequest update) {
        DesignDraftFileAction action = update.getBackgroundMusicAction() == null
                ? DesignDraftFileAction.KEEP
                : update.getBackgroundMusicAction();
        draft.setBackgroundMusicAction(action);
        draft.setBackgroundMusicAsset(switch (action) {
            case REPLACE -> requireAsset(
                    request,
                    update.getBackgroundMusicAssetId(),
                    DesignDraftAssetType.BACKGROUND_MUSIC);
            case KEEP -> findBaselineAsset(
                    request,
                    request.getBooth().getBackgroundMusicPublicId(),
                    DesignDraftAssetType.BACKGROUND_MUSIC);
            case CLEAR -> null;
        });
    }

    private DesignDraftAsset findBaselineAsset(
            DesignRequest request,
            String publicId,
            DesignDraftAssetType type) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return draftAssetRepository.findByDesignRequestIdAndPublicId(request.getId(), publicId)
                .filter(asset -> asset.getAssetType() == type)
                .orElse(null);
    }

    private DesignDraftAsset requireAsset(
            DesignRequest request,
            UUID assetId,
            DesignDraftAssetType type) {
        if (assetId == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return assetService.requireDraftAsset(request, assetId, type);
    }

    private void applyHotspot(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertHotspotRequest update) {
        if (update == null || update.getType() == null
                || update.getXPosition() == null
                || update.getYPosition() == null
                || update.getZPosition() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        clearHotspotContent(hotspot);
        hotspot.setType(update.getType());
        hotspot.setXPosition(update.getXPosition());
        hotspot.setYPosition(update.getYPosition());
        hotspot.setZPosition(update.getZPosition());
        hotspot.setIconStyle(trimToNull(update.getIconStyle()));
        hotspot.setScale(update.getScale());
        hotspot.setZIndex(update.getZIndex());
        applyCorners(hotspot, update.getType(), update.getCorners());
        switch (update.getType()) {
            case NAV -> applyNavHotspot(draft, hotspot, update);
            case PRODUCT -> applyProductHotspot(request, hotspot, update);
            case INFO -> applyInfoHotspot(request, hotspot, update);
            case MEDIA -> applyMediaHotspot(request, hotspot, update);
            default -> throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
    }

    private void applyNavHotspot(
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertHotspotRequest update) {
        if (update.getTargetPanoramaId() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraftPanorama target = getPanorama(draft, update.getTargetPanoramaId());
        hotspot.setTargetDraftPanoramaKey(target.getClientKey());
        hotspot.setName(resolveName(update.getName(), target.getName()));
    }

    private void applyProductHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            UpsertHotspotRequest update) {
        if (update.getProductId() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        requestProductService.assertProductAllowed(request, update.getProductId());
        Product product = productService.getProductForCompany(update.getProductId(), request.getCompany());
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(update.getName(), product.getName()));
    }

    private void applyInfoHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            UpsertHotspotRequest update) {
        HotspotInfoContentType contentType = resolveInfoContentType(update);
        hotspot.setInfoContentType(contentType);
        hotspot.setName(resolveName(update.getName(), "Info"));
        switch (contentType) {
            case NONE -> {
                // INFO/NONE intentionally has no attached content.
            }
            case TEXT -> {
                String text = trimToNull(update.getInfoText());
                if (text == null) {
                    throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
                }
                hotspot.setInfoText(text);
            }
            case PRODUCT -> applyProductHotspot(request, hotspot, update);
            case IMAGE -> hotspot.setMediaAsset(getMediaAsset(request, update.getMediaAssetId(), MediaAssetType.IMAGE));
            case VIDEO -> hotspot.setMediaAsset(getMediaAsset(request, update.getMediaAssetId(), MediaAssetType.VIDEO));
            default -> throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
    }

    private void applyMediaHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            UpsertHotspotRequest update) {
        MediaAsset mediaAsset = getMediaAsset(request, update.getMediaAssetId(), null);
        hotspot.setMediaAsset(mediaAsset);
        hotspot.setMediaClickAction(update.getMediaClickAction() == null
                ? HotspotMediaClickAction.DEFAULT
                : update.getMediaClickAction());
        hotspot.setName(resolveName(update.getName(), mediaAsset.getName()));
    }

    private MediaAsset getMediaAsset(
            DesignRequest request,
            UUID mediaAssetId,
            MediaAssetType expectedType) {
        return boothDesignService.getMediaAssetForCompany(
                mediaAssetId,
                request.getCompany().getId(),
                expectedType);
    }

    private HotspotInfoContentType resolveInfoContentType(UpsertHotspotRequest update) {
        if (update.getInfoContentType() != null) {
            return update.getInfoContentType();
        }
        if (update.getProductId() != null) {
            return HotspotInfoContentType.PRODUCT;
        }
        if (update.getMediaAssetId() != null) {
            return HotspotInfoContentType.IMAGE;
        }
        if (trimToNull(update.getInfoText()) != null) {
            return HotspotInfoContentType.TEXT;
        }
        return HotspotInfoContentType.NONE;
    }

    private void clearHotspotContent(DesignDraftHotspot hotspot) {
        hotspot.setTargetDraftPanoramaKey(null);
        hotspot.setProduct(null);
        hotspot.setMediaAsset(null);
        hotspot.setInfoText(null);
        hotspot.setMediaClickAction(null);
        hotspot.setInfoContentType(null);
        hotspot.setCornerTlX(null);
        hotspot.setCornerTlY(null);
        hotspot.setCornerTlZ(null);
        hotspot.setCornerTrX(null);
        hotspot.setCornerTrY(null);
        hotspot.setCornerTrZ(null);
        hotspot.setCornerBlX(null);
        hotspot.setCornerBlY(null);
        hotspot.setCornerBlZ(null);
        hotspot.setCornerBrX(null);
        hotspot.setCornerBrY(null);
        hotspot.setCornerBrZ(null);
    }

    private void applyCorners(DesignDraftHotspot hotspot, HotspotType type, HotspotCornersDTO corners) {
        if (type != HotspotType.MEDIA && type != HotspotType.PRODUCT || corners == null) {
            return;
        }
        if (!isCorner(corners.getTl()) || !isCorner(corners.getTr())
                || !isCorner(corners.getBl()) || !isCorner(corners.getBr())) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        hotspot.setCornerTlX(corners.getTl().get(0));
        hotspot.setCornerTlY(corners.getTl().get(1));
        hotspot.setCornerTlZ(corners.getTl().get(2));
        hotspot.setCornerTrX(corners.getTr().get(0));
        hotspot.setCornerTrY(corners.getTr().get(1));
        hotspot.setCornerTrZ(corners.getTr().get(2));
        hotspot.setCornerBlX(corners.getBl().get(0));
        hotspot.setCornerBlY(corners.getBl().get(1));
        hotspot.setCornerBlZ(corners.getBl().get(2));
        hotspot.setCornerBrX(corners.getBr().get(0));
        hotspot.setCornerBrY(corners.getBr().get(1));
        hotspot.setCornerBrZ(corners.getBr().get(2));
    }

    private boolean isCorner(List<Double> corner) {
        return corner != null && corner.size() == 3
                && corner.get(0) != null && corner.get(1) != null && corner.get(2) != null;
    }

    private void normalizePanoramas(DesignDraft draft) {
        List<DesignDraftPanorama> ordered = new ArrayList<>(draft.getPanoramas());
        ordered.sort(Comparator.comparing(
                DesignDraftPanorama::getOrderIndex,
                Comparator.nullsLast(Integer::compareTo)));
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setOrderIndex(index);
        }
        if (ordered.isEmpty()) {
            return;
        }
        List<DesignDraftPanorama> defaults = ordered.stream()
                .filter(panorama -> Boolean.TRUE.equals(panorama.getIsDefault()))
                .toList();
        DesignDraftPanorama selected = defaults.isEmpty() ? ordered.get(0) : defaults.get(0);
        for (DesignDraftPanorama panorama : ordered) {
            panorama.setIsDefault(panorama == selected);
        }
    }

    private void clearDefault(DesignDraft draft, DesignDraftPanorama except) {
        for (DesignDraftPanorama panorama : draft.getPanoramas()) {
            if (panorama != except) {
                panorama.setIsDefault(false);
            }
        }
    }

    private int resolveInsertionIndex(Integer requested, int size) {
        if (requested == null) {
            return size;
        }
        if (requested < 0 || requested > size) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return requested;
    }

    private DesignDraftPanorama getPanorama(DesignDraft draft, UUID panoramaId) {
        if (panoramaId == null) {
            throw new AppException(ErrorCode.PANORAMA_NOT_FOUND);
        }
        return draft.getPanoramas().stream()
                .filter(panorama -> panoramaId.equals(panorama.getId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private DesignDraftHotspot getHotspot(DesignDraftPanorama source, UUID hotspotId) {
        if (hotspotId == null) {
            throw new AppException(ErrorCode.HOTSPOT_NOT_FOUND);
        }
        return source.getHotspots().stream()
                .filter(hotspot -> hotspotId.equals(hotspot.getId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));
    }

    private String resolveName(String provided, String fallback) {
        String normalized = trimToNull(provided);
        return normalized == null ? requireText(fallback) : normalized;
    }

    private String requireText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record EditableDraft(DesignRequest request, DesignDraft draft) {
    }
}
