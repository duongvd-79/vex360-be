package com.example.vex360.features.designrequest.services;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.designrequest.dtos.request.DesignDraftBoothSettingsRequest;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Service responsible for applying booth configuration settings (name,
 * description, display template,
 * thumbnail, and background music actions) to drafts and booths.
 */
@Service
@RequiredArgsConstructor
public class DesignDraftSettingsService {
    private final DesignDraftAssetService assetService;
    private final DesignAssetReferenceService assetReferenceService;
    private final DesignDraftAssetRepository assetRepository;

    /**
     * Applies the booth settings from a draft request to a given DesignDraft.
     *
     * @param request  the design request
     * @param draft    the draft to apply settings to
     * @param settings the booth settings request payload
     * @throws AppException if the settings are invalid
     */
    public void applyToDraft(
            DesignRequest request,
            DesignDraft draft,
            DesignDraftBoothSettingsRequest settings) {
        Booth booth = request.getBooth();
        draft.setBoothName(settings == null || settings.getName() == null
                ? booth.getName()
                : requireText(settings.getName()));
        draft.setBoothDescription(settings == null || settings.getDescription() == null
                ? booth.getDescription()
                : trimToNull(settings.getDescription()));
        draft.setDisplayTemplateKey(settings == null || settings.getDisplayTemplateKey() == null
                ? booth.getDisplayTemplateKey()
                : requireText(settings.getDisplayTemplateKey()));

        DesignDraftFileAction thumbnailAction = settings == null || settings.getThumbnailAction() == null
                ? DesignDraftFileAction.KEEP
                : settings.getThumbnailAction();
        DesignDraftFileAction musicAction = settings == null || settings.getBackgroundMusicAction() == null
                ? DesignDraftFileAction.KEEP
                : settings.getBackgroundMusicAction();
        draft.setThumbnailAction(thumbnailAction);
        draft.setBackgroundMusicAction(musicAction);
        if (thumbnailAction == DesignDraftFileAction.REPLACE) {
            if (settings.getThumbnailAssetId() == null) {
                throw new AppException(ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID);
            }
            draft.setThumbnailAsset(assetService.requireDraftAsset(
                    request, settings.getThumbnailAssetId(), DesignDraftAssetType.THUMBNAIL));
        } else if (thumbnailAction == DesignDraftFileAction.KEEP) {
            draft.setThumbnailAsset(findBaselineAsset(
                    request,
                    request.getBooth().getThumbnailPublicId(),
                    DesignDraftAssetType.THUMBNAIL));
        }
        if (musicAction == DesignDraftFileAction.REPLACE) {
            if (settings.getBackgroundMusicAssetId() == null) {
                throw new AppException(ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID);
            }
            draft.setBackgroundMusicAsset(assetService.requireDraftAsset(
                    request, settings.getBackgroundMusicAssetId(), DesignDraftAssetType.BACKGROUND_MUSIC));
        } else if (musicAction == DesignDraftFileAction.KEEP) {
            draft.setBackgroundMusicAsset(findBaselineAsset(
                    request,
                    request.getBooth().getBackgroundMusicPublicId(),
                    DesignDraftAssetType.BACKGROUND_MUSIC));
        }
    }

    /**
     * Applies the settings defined in a DesignDraft back to the official Booth.
     * Manages replacement/deletion of previous thumbnail and background music
     * assets.
     *
     * @param booth the target booth to modify
     * @param draft the source draft containing approved configuration settings
     */
    public void applyToBooth(Booth booth, DesignDraft draft) {
        String oldThumbnailPublicId = booth.getThumbnailPublicId();
        String oldMusicPublicId = booth.getBackgroundMusicPublicId();
        booth.setName(draft.getBoothName());
        booth.setDescription(draft.getBoothDescription());
        booth.setDisplayTemplateKey(draft.getDisplayTemplateKey());
        applyThumbnail(booth, draft.getThumbnailAction(), draft.getThumbnailAsset());
        applyBackgroundMusic(booth, draft.getBackgroundMusicAction(), draft.getBackgroundMusicAsset());
        cleanupReplacedAsset(oldThumbnailPublicId, booth.getThumbnailPublicId(), "image");
        cleanupReplacedAsset(oldMusicPublicId, booth.getBackgroundMusicPublicId(), "video");
    }

    private void applyThumbnail(Booth booth, DesignDraftFileAction action, DesignDraftAsset asset) {
        if (action == DesignDraftFileAction.KEEP) {
            return;
        }
        if (action == DesignDraftFileAction.CLEAR) {
            booth.setThumbnailUrl(null);
            booth.setThumbnailPublicId(null);
            return;
        }
        requireAsset(asset);
        booth.setThumbnailUrl(asset.getUrl());
        booth.setThumbnailPublicId(asset.getPublicId());
    }

    private void applyBackgroundMusic(Booth booth, DesignDraftFileAction action, DesignDraftAsset asset) {
        if (action == DesignDraftFileAction.KEEP) {
            return;
        }
        if (action == DesignDraftFileAction.CLEAR) {
            booth.setBackgroundMusicUrl(null);
            booth.setBackgroundMusicPublicId(null);
            booth.setBackgroundMusicFileName(null);
            booth.setBackgroundMusicFileSize(null);
            return;
        }
        requireAsset(asset);
        booth.setBackgroundMusicUrl(asset.getUrl());
        booth.setBackgroundMusicPublicId(asset.getPublicId());
        booth.setBackgroundMusicFileName(asset.getFileName());
        booth.setBackgroundMusicFileSize(asset.getFileSize());
    }

    private void requireAsset(DesignDraftAsset asset) {
        if (asset == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_ASSET_NOT_FOUND);
        }
    }

    private void cleanupReplacedAsset(
            String oldPublicId,
            String currentPublicId,
            String resourceType) {
        if (oldPublicId == null || oldPublicId.isBlank() || oldPublicId.equals(currentPublicId)) {
            return;
        }
        assetReferenceService.scheduleCleanup(oldPublicId, resourceType);
    }

    private DesignDraftAsset findBaselineAsset(
            DesignRequest request,
            String publicId,
            DesignDraftAssetType type) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return assetRepository.findByDesignRequestIdAndPublicId(request.getId(), publicId)
                .filter(asset -> asset.getAssetType() == type)
                .orElse(null);
    }

    private String requireText(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_SETTINGS_INVALID);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
