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
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.ReorderDesignDraftPanoramasRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftMediaAssetRequest;
import com.example.vex360.features.designrequest.dtos.request.UpsertDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftSettingsRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftHotspotResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftMediaAssetResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPanoramaResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftSettingsResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;

import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftHotspotRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftMediaAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
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
    private final DesignDraftPanoramaRepository draftPanoramaRepository;
    private final DesignDraftHotspotRepository draftHotspotRepository;
    private final DesignDraftMediaAssetRepository draftMediaAssetRepository;
    private final DesignDraftAssetService assetService;
    private final DesignRequestProductService requestProductService;
    private final DesignRequestMediaAssetService requestMediaAssetService;
    private final ProductService productService;
    private final BoothDesignService boothDesignService;
    private final DesignDraftBenefitGuardService benefitGuardService;
    private final DesignDraftGraphValidator graphValidator;
    private final DesignerDraftPreviewService previewService;
    private final DesignRequestMapper designRequestMapper;
    private final DesignRequestLifecyclePolicy lifecyclePolicy;

    @Transactional
    public DesignDraftSettingsResponseDTO updateSettings(
            User designer,
            UUID requestId,
            long expectedRevision,
            UpdateDesignDraftSettingsRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        if (update == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID);
        }
        DesignDraft draft = context.draft();
        draft.setNote(trimToNull(update.getNote()));
        draft.setBoothName(requireText(update.getName(), ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID));
        draft.setBoothDescription(trimToNull(update.getDescription()));
        draft.setDisplayTemplateKey(
                requireText(update.getDisplayTemplateKey(), ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID));
        applyThumbnailSettings(context.request(), draft, update);
        applyMusicSettings(context.request(), draft, update);
        graphValidator.validateWorkingGraph(context.request(), draft);
        bumpRevision(draft);
        DesignDraft saved = draftRepository.saveAndFlush(draft);
        return previewService.toSettingsResponse(context.request(), saved);
    }

    @Transactional
    public DesignDraftPanoramaResponseDTO createPanorama(
            User designer,
            UUID requestId,
            long expectedRevision,
            CreateDesignDraftPanoramaRequest create) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        if (create == null || create.getPanoramaAssetId() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID);
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
                .name(requireText(create.getName(), ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID))
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
        bumpRevision(draft);
        draftPanoramaRepository.saveAndFlush(panorama);
        return previewService.toPanoramaResponse(panorama);
    }

    @Transactional
    public DesignDraftPanoramaResponseDTO updatePanorama(
            User designer,
            UUID requestId,
            UUID panoramaId,
            long expectedRevision,
            UpdateDesignDraftPanoramaRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        if (update == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID);
        }
        DesignDraft draft = context.draft();
        DesignDraftPanorama panorama = getPanorama(draft, panoramaId);
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        if (update.getName() != null) {
            panorama.setName(requireText(update.getName(), ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID));
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
        bumpRevision(draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toPanoramaResponse(panorama);
    }

    @Transactional
    public List<DesignDraftPanoramaResponseDTO> reorderPanoramas(
            User designer,
            UUID requestId,
            long expectedRevision,
            ReorderDesignDraftPanoramasRequest reorder) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        if (reorder == null || reorder.getPanoramaIds() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID);
        }
        DesignDraft draft = context.draft();
        List<UUID> requestedIds = reorder.getPanoramaIds();
        Set<UUID> uniqueIds = new HashSet<>(requestedIds);
        Set<UUID> currentIds = draft.getPanoramas().stream()
                .map(DesignDraftPanorama::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (uniqueIds.size() != requestedIds.size() || !uniqueIds.equals(currentIds)) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID);
        }
        for (int index = 0; index < requestedIds.size(); index++) {
            getPanorama(draft, requestedIds.get(index)).setOrderIndex(index);
        }
        normalizePanoramas(draft);
        graphValidator.validateWorkingGraph(context.request(), draft);
        bumpRevision(draft);
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
            long expectedRevision,
            UUID panoramaId) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
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
        bumpRevision(draft);
        draftRepository.saveAndFlush(draft);
        return response;
    }

    @Transactional
    public DesignDraftHotspotResponseDTO createHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            long expectedRevision,
            UpsertDesignDraftHotspotRequest create) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
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
        bumpRevision(draft);
        draftHotspotRepository.saveAndFlush(hotspot);
        return previewService.toHotspotResponse(hotspot);
    }

    @Transactional
    public DesignDraftHotspotResponseDTO updateHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            UUID hotspotId,
            long expectedRevision,
            UpsertDesignDraftHotspotRequest update) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        DesignDraft draft = context.draft();
        DesignDraftPanorama source = getPanorama(draft, panoramaId);
        DesignDraftHotspot hotspot = getHotspot(source, hotspotId);
        DesignDraftBenefitGuardService.Usage beforeUsage = benefitGuardService.calculateUsage(draft);
        applyHotspot(context.request(), draft, hotspot, update);
        graphValidator.validateWorkingGraph(context.request(), draft);
        benefitGuardService.assertMutationAllowed(context.request(), beforeUsage, draft);
        bumpRevision(draft);
        draftRepository.saveAndFlush(draft);
        return previewService.toHotspotResponse(hotspot);
    }

    @Transactional
    public DesignDraftHotspotResponseDTO deleteHotspot(
            User designer,
            UUID requestId,
            UUID panoramaId,
            long expectedRevision,
            UUID hotspotId) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        DesignDraftPanorama source = getPanorama(context.draft(), panoramaId);
        DesignDraftHotspot hotspot = getHotspot(source, hotspotId);
        DesignDraftHotspotResponseDTO response = previewService.toHotspotResponse(hotspot);
        source.getHotspots().remove(hotspot);
        graphValidator.validateWorkingGraph(context.request(), context.draft());
        bumpRevision(context.draft());
        draftRepository.saveAndFlush(context.draft());
        return response;
    }

    private EditableDraft getEditableDraft(User designer, UUID requestId, long expectedRevision) {
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
        lifecyclePolicy.assertCanContinue(request);
        DesignDraft draft = draftRepository.findByDesignRequestIdAndVersionNumber(requestId, WORKING_VERSION)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_NOT_FOUND));
        if (expectedRevision != (draft.getRevision() == null ? 0L : draft.getRevision())) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_EDIT_CONFLICT);
        }
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
            throw new AppException(ErrorCode.DESIGN_DRAFT_ASSET_NOT_FOUND);
        }
        return assetService.requireDraftAsset(request, assetId, type);
    }

    private void applyHotspot(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update) {
        if (update == null || update.getType() == null
                || update.getXPosition() == null
                || update.getYPosition() == null
                || update.getZPosition() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
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
            case INFO -> applyInfoHotspot(request, draft, hotspot, update);
            case MEDIA -> applyMediaHotspot(request, draft, hotspot, update);
            default -> throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
    }

    private void applyNavHotspot(
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update) {
        if (update.getTargetPanoramaId() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_REFERENCE_INVALID);
        }
        DesignDraftPanorama target = getPanorama(draft, update.getTargetPanoramaId());
        hotspot.setTargetDraftPanoramaKey(target.getClientKey());
        hotspot.setName(resolveName(update.getName(), target.getName()));
    }

    private void applyProductHotspot(
            DesignRequest request,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update) {
        if (update.getProductId() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PRODUCT_REFERENCE_INVALID);
        }
        requestProductService.assertProductAllowed(request, update.getProductId());
        Product product = productService.getProductForCompany(update.getProductId(), request.getCompany());
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_PRODUCT_REFERENCE_INVALID);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(update.getName(), product.getName()));
    }

    private void applyInfoHotspot(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update) {
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
                    throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
                }
                hotspot.setInfoText(text);
            }
            case PRODUCT -> applyProductHotspot(request, hotspot, update);
            case IMAGE -> applyMediaReference(request, draft, hotspot, update, MediaAssetType.IMAGE);
            case VIDEO -> applyMediaReference(request, draft, hotspot, update, MediaAssetType.VIDEO);
            default -> throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
        }
    }

    private void applyMediaHotspot(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update) {
        applyMediaReference(request, draft, hotspot, update, null);
        hotspot.setMediaClickAction(update.getMediaClickAction() == null
                ? HotspotMediaClickAction.DEFAULT
                : update.getMediaClickAction());
        hotspot.setName(resolveName(update.getName(), mediaName(hotspot)));
    }

    private MediaAsset getMediaAsset(
            DesignRequest request,
            UUID mediaAssetId,
            MediaAssetType expectedType) {
        requestMediaAssetService.assertMediaAssetAllowed(request, mediaAssetId);
        return boothDesignService.getMediaAssetForCompany(
                mediaAssetId,
                request.getCompany().getId(),
                expectedType);
    }

    private void applyMediaReference(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftHotspot hotspot,
            UpsertDesignDraftHotspotRequest update,
            MediaAssetType expectedType) {
        boolean officialProvided = update.getMediaAssetId() != null;
        boolean draftProvided = update.getDesignDraftMediaAssetId() != null;
        if (officialProvided == draftProvided) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        if (officialProvided) {
            hotspot.setMediaAsset(getMediaAsset(request, update.getMediaAssetId(), expectedType));
            return;
        }
        DesignDraftMediaAsset draftMedia = draft.getMediaAssets().stream()
                .filter(media -> update.getDesignDraftMediaAssetId().equals(media.getId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID));
        if (draftMedia.getAsset() == null
                || draftMedia.getAsset().getAssetType() != DesignDraftAssetType.MEDIA_ATTACHMENT
                || expectedType != null && mediaType(draftMedia) != expectedType) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        hotspot.setDesignDraftMediaAsset(draftMedia);
    }

    private MediaAssetType mediaType(DesignDraftMediaAsset media) {
        String mimeType = media.getAsset().getMimeType();
        if ("video/mp4".equalsIgnoreCase(mimeType)) {
            return MediaAssetType.VIDEO;
        }
        if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType)) {
            return MediaAssetType.IMAGE;
        }
        throw new AppException(ErrorCode.DESIGN_DRAFT_ASSET_TYPE_INVALID);
    }

    private String mediaName(DesignDraftHotspot hotspot) {
        if (hotspot.getMediaAsset() != null) {
            return hotspot.getMediaAsset().getName();
        }
        DesignDraftMediaAsset media = hotspot.getDesignDraftMediaAsset();
        if (media == null || media.getAsset() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        String title = trimToNull(media.getTitle());
        return title == null ? media.getAsset().getFileName() : title;
    }

    private HotspotInfoContentType resolveInfoContentType(UpsertDesignDraftHotspotRequest update) {
        if (update.getInfoContentType() != null) {
            return update.getInfoContentType();
        }
        if (update.getProductId() != null) {
            return HotspotInfoContentType.PRODUCT;
        }
        if (update.getMediaAssetId() != null || update.getDesignDraftMediaAssetId() != null) {
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
        hotspot.setDesignDraftMediaAsset(null);
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
            throw new AppException(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID);
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
            throw new AppException(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID);
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
        return requireText(value, ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID);
    }

    private String requireText(String value, ErrorCode errorCode) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new AppException(errorCode);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public DesignDraftMediaAssetResponseDTO addMediaAsset(
            User designer,
            UUID requestId,
            long expectedRevision,
            SubmitDesignDraftMediaAssetRequest request) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        if (request == null || request.getAssetId() == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        DesignDraft draft = context.draft();
        boolean duplicate = draft.getMediaAssets().stream()
                .anyMatch(ma -> ma.getAsset() != null && request.getAssetId().equals(ma.getAsset().getId()));
        if (duplicate) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_DUPLICATED);
        }
        DesignDraftAsset asset = draftAssetRepository
                .findByIdAndDesignRequestId(request.getAssetId(), context.request().getId())
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_ASSET_NOT_FOUND));
        if (asset.getAssetType() != DesignDraftAssetType.MEDIA_ATTACHMENT) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_ASSET_TYPE_INVALID);
        }
        String title = trimToNull(request.getTitle());
        if (title != null && title.length() > 255) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_ASSET_NAME_INVALID);
        }

        DesignDraftMediaAsset mediaAsset = DesignDraftMediaAsset.builder()
                .draft(draft)
                .asset(asset)
                .title(title)
                .sortOrder(nextMediaSortOrder(draft))
                .build();
        draft.getMediaAssets().add(mediaAsset);
        bumpRevision(draft);
        draftMediaAssetRepository.saveAndFlush(mediaAsset);
        return designRequestMapper.toMediaAssetResponse(mediaAsset);
    }

    @Transactional
    public List<DesignDraftMediaAssetResponseDTO> reorderMediaAssets(
            User designer,
            UUID requestId,
            long expectedRevision,
            List<UUID> mediaAssetIds) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        DesignDraft draft = context.draft();
        if (mediaAssetIds == null || mediaAssetIds.size() != draft.getMediaAssets().size()) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_ORDER_INVALID);
        }
        Set<UUID> ids = new HashSet<>(mediaAssetIds);
        if (ids.size() != mediaAssetIds.size()) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_ORDER_INVALID);
        }
        for (int index = 0; index < mediaAssetIds.size(); index++) {
            UUID targetId = mediaAssetIds.get(index);
            draft.getMediaAssets().stream()
                    .filter(ma -> Objects.equals(ma.getId(), targetId))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_ORDER_INVALID))
                    .setSortOrder(index);
        }
        bumpRevision(draft);
        draftRepository.saveAndFlush(draft);
        return draft.getMediaAssets().stream()
                .sorted(Comparator.comparing(DesignDraftMediaAsset::getSortOrder))
                .map(designRequestMapper::toMediaAssetResponse)
                .toList();
    }

    @Transactional
    public void removeMediaAsset(User designer, UUID requestId, long expectedRevision, UUID mediaAssetId) {
        EditableDraft context = getEditableDraft(designer, requestId, expectedRevision);
        DesignDraft draft = context.draft();
        DesignDraftMediaAsset media = draft.getMediaAssets().stream()
                .filter(ma -> Objects.equals(ma.getId(), mediaAssetId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID));
        boolean usedInHotspot = draft.getPanoramas().stream()
                .flatMap(p -> p.getHotspots().stream())
                .anyMatch(h -> h.getDesignDraftMediaAsset() != null
                        && Objects.equals(h.getDesignDraftMediaAsset().getId(), mediaAssetId));
        if (usedInHotspot) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_IN_USE);
        }
        draft.getMediaAssets().remove(media);
        bumpRevision(draft);
        draftRepository.saveAndFlush(draft);
    }

    private int nextMediaSortOrder(DesignDraft draft) {
        return draft.getMediaAssets().stream()
                .map(DesignDraftMediaAsset::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private void bumpRevision(DesignDraft draft) {
        draft.setRevision((draft.getRevision() == null ? 0L : draft.getRevision()) + 1);
    }

    private record EditableDraft(DesignRequest request, DesignDraft draft) {
    }
}
