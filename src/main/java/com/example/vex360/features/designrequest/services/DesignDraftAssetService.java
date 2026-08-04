package com.example.vex360.features.designrequest.services;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftMediaAssetRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages request-scoped assets while a Designer prepares booth drafts.
 * Designer panorama and media uploads remain staged until an Exhibitor approves
 * a draft that actually references them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignDraftAssetService {
    private static final long MAX_FILE_SIZE = (long) 10 * 1024 * 1024;
    private static final Set<String> PANORAMA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> MUSIC_TYPES = Set.of("audio/mpeg", "audio/mp3");
    private static final Set<String> MEDIA_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");

    private final DesignDraftAssetRepository assetRepository;
    private final DesignDraftMediaAssetRepository draftMediaAssetRepository;
    private final DesignerWorkspaceService workspaceService;
    private final CompanyStorageService storageService;
    private final CloudService cloudService;
    private final BoothDesignService boothDesignService;
    private final DesignAssetReferenceService assetReferenceService;

    /**
     * Uploads a panorama staging asset for an assigned, editable request.
     * Accepted files are JPEG, PNG, or WEBP images up to 10 MB. Upload does not
     * change company storage counters; a failed database operation removes the
     * uploaded cloud asset.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param file        panorama image to upload
     * @return metadata used to reference the asset from a draft panorama
     * @throws AppException if assignment, request status, file validation, or
     *                      upload fails
     */
    @Transactional
    public DesignDraftAssetResponseDTO uploadPanorama(User currentUser, UUID requestId, MultipartFile file) {
        return uploadAsset(currentUser, requestId, file, DesignDraftAssetType.PANORAMA);
    }

    /**
     * Uploads a staging asset (panorama, thumbnail, or background music) for an
     * assigned request.
     * Validates and uploads the file without changing company storage counters.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param file        the file to upload
     * @param assetType   the type of asset (PANORAMA, THUMBNAIL, or
     *                    BACKGROUND_MUSIC)
     * @return the uploaded asset response DTO
     * @throws AppException if validation or upload fails
     */
    @Transactional
    public DesignDraftAssetResponseDTO uploadAsset(
            User currentUser,
            UUID requestId,
            MultipartFile file,
            DesignDraftAssetType assetType) {
        DesignRequest request = workspaceService.getAssignedRequestForUpdate(currentUser, requestId);
        requireEditableRequest(request);
        DesignDraftAssetType resolvedType = assetType == null ? DesignDraftAssetType.PANORAMA : assetType;
        validateFile(file, resolvedType);
        String folder = switch (resolvedType) {
            case PANORAMA -> FileUploadUtils.PANORAMA_FOLDER;
            case THUMBNAIL -> "design-draft-thumbnail";
            case BACKGROUND_MUSIC -> "design-draft-background-music";
            case MEDIA_ATTACHMENT -> "design-draft-media-attachment";
            case MODEL_3D -> "design-draft-model-3d";
        };

        CloudinaryResponse upload = cloudService.uploadToFolder(file, folder);
        boolean rollbackCleanupRegistered = registerRollbackCleanup(
                upload.getPublicId(),
                resourceType(resolvedType, upload.getFileType()));
        long fileSize = upload.getFileSize() == null ? file.getSize() : upload.getFileSize();
        try {
            DesignDraftAsset asset = assetRepository.save(DesignDraftAsset.builder()
                    .designRequest(request)
                    .uploadedBy(currentUser)
                    .url(upload.getUrl())
                    .publicId(upload.getPublicId())
                    .fileName(upload.getFileName())
                    .mimeType(upload.getFileType())
                    .fileSize(fileSize)
                    .assetType(resolvedType)
                    .assetSource(DesignDraftAssetSource.UPLOADED)
                    .quotaState(resolvedType == DesignDraftAssetType.MEDIA_ATTACHMENT
                            ? DesignDraftAssetQuotaState.STAGED
                            : DesignDraftAssetQuotaState.NONE)
                    .build());
            return toResponse(asset);
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                cleanupCloudAsset(upload.getPublicId(), resourceType(resolvedType, upload.getFileType()));
            }
            throw exception;
        }
    }

    /**
     * Deletes an unused staging asset. Legacy charged or reserved assets are
     * settled according to their existing quota state.
     * An asset referenced by any draft or by the current booth cannot be
     * released manually.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param assetId     staging asset identifier
     * @return metadata of the deleted asset
     * @throws AppException if the request is not editable, the Designer is not
     *                      assigned, the asset does not belong to the request, or
     *                      it is in use
     */
    @Transactional
    public DesignDraftAssetResponseDTO releaseAsset(User currentUser, UUID requestId, UUID assetId) {
        DesignRequest request = workspaceService.getAssignedRequestForUpdate(currentUser, requestId);
        requireEditableRequest(request);
        DesignDraftAsset asset = assetRepository.findByIdAndDesignRequestId(assetId, requestId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        pruneUnreferencedDraftMedia(request, asset.getId());
        if (isReferencedByDraft(request, asset.getPublicId()) || isUsedByBooth(request, asset.getPublicId())) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        deleteAsset(asset);
        return toResponse(asset);
    }

    /**
     * Lists staging assets uploaded for an assigned request.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param pageable    pagination and sorting options
     * @return a page of request-scoped asset metadata
     * @throws AppException if the Designer is not assigned to the request
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignDraftAssetResponseDTO> getAssets(
            User currentUser,
            UUID requestId,
            Pageable pageable) {
        workspaceService.getAssignedRequest(currentUser, requestId);
        return PageResponse.from(assetRepository.findByDesignRequestId(requestId, pageable)
                .map(this::toResponse));
    }

    @Transactional
    public DesignDraftAssetResponseDTO renameAsset(
            User currentUser,
            UUID requestId,
            UUID assetId,
            String newName) {
        DesignRequest request = workspaceService.getAssignedRequestForUpdate(currentUser, requestId);
        requireEditableRequest(request);
        String normalized = newName == null ? null : newName.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 255) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraftAsset asset = assetRepository.findByIdAndDesignRequestId(assetId, requestId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        if (asset.getAssetType() != DesignDraftAssetType.MEDIA_ATTACHMENT) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        asset.setFileName(normalized);
        request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() == 0 && draft.getMediaAssets() != null)
                .flatMap(draft -> draft.getMediaAssets().stream())
                .filter(media -> media.getAsset() != null && assetId.equals(media.getAsset().getId()))
                .forEach(media -> media.setTitle(normalized));
        return toResponse(assetRepository.save(asset));
    }


    /**
     * Removes assets from this request that are not referenced by any saved
     * draft or by the current booth. This is used after draft replacement or
     * submission to remove abandoned uploads.
     *
     * @param request managed request aggregate with its drafts available
     * @return number of assets deleted
     */
    @Transactional
    public int cleanupUnreferencedAssets(DesignRequest request) {
        pruneUnreferencedDraftMedia(request);
        Set<String> referencedKeys = draftImageKeys(request);
        referencedKeys.addAll(boothImageKeys(request));
        return deleteUnreferenced(assetRepository.findByDesignRequestId(request.getId()), referencedKeys);
    }

    /**
     * Removes all design-request assets for the same booth that are not used by
     * the booth's currently applied panorama set. Approved panorama assets are
     * retained while superseded draft assets are deleted.
     *
     * @param request approved or terminal request identifying the target booth
     * @return number of assets deleted
     */
    @Transactional
    public int cleanupAfterApproval(DesignRequest request) {
        return deleteUnreferenced(
                assetRepository.findByDesignRequestBoothId(request.getBooth().getId()),
                boothImageKeys(request));
    }

    /**
     * Resolves a staging asset by request and public ID, and verifies that the
     * supplied URL matches the persisted upload. This prevents drafts from
     * referencing external or cross-request images.
     *
     * @param request  request that must own the asset
     * @param publicId cloud public ID supplied by the draft
     * @param url      cloud URL supplied by the draft
     * @return the validated staging asset
     * @throws AppException if the asset is missing or the URL does not match
     */
    @Transactional(readOnly = true)
    public DesignDraftAsset requireDraftAsset(DesignRequest request, String publicId, String url) {
        DesignDraftAsset asset = assetRepository.findByDesignRequestIdAndPublicId(request.getId(), publicId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        if (!asset.getUrl().equals(url) || asset.getAssetType() != DesignDraftAssetType.PANORAMA) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return asset;
    }

    /**
     * Resolves a staging asset by request, asset ID, and verifies its type.
     * Prevents drafts from referencing incorrect asset types or assets from other
     * requests.
     *
     * @param request   the design request that must own the asset
     * @param assetId   the identifier of the asset
     * @param assetType the expected type of the asset
     * @return the verified draft asset entity
     * @throws AppException if the asset is missing, or doesn't belong to the
     *                      request, or type mismatch
     */
    @Transactional(readOnly = true)
    public DesignDraftAsset requireDraftAsset(
            DesignRequest request,
            UUID assetId,
            DesignDraftAssetType assetType) {
        DesignDraftAsset asset = assetRepository.findByIdAndDesignRequestId(assetId, request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        if (asset.getAssetType() != assetType) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return asset;
    }

    private int deleteUnreferenced(List<DesignDraftAsset> assets, Set<String> referencedKeys) {
        int deleted = 0;
        for (DesignDraftAsset asset : assets) {
            if (!referencedKeys.contains(asset.getPublicId())
                    && !assetReferenceService.isReferenced(asset.getPublicId())) {
                deleteAsset(asset);
                deleted++;
            }
        }
        return deleted;
    }

    private void deleteAsset(DesignDraftAsset asset) {
        assetRepository.delete(asset);
        DesignDraftAssetQuotaState quotaState = asset.getQuotaState();
        if (quotaState == null) {
            quotaState = asset.getAssetSource() == DesignDraftAssetSource.BOOTH_BASELINE
                    ? DesignDraftAssetQuotaState.NONE
                    : DesignDraftAssetQuotaState.CHARGED;
        }
        switch (quotaState) {
            case CHARGED -> storageService.deductUsage(
                    asset.getDesignRequest().getCompany(),
                    asset.getFileSize());
            case RESERVED -> storageService.releaseReservedUsage(
                    asset.getDesignRequest().getCompany(),
                    asset.getFileSize());
            case NONE, STAGED, PROMOTED -> {
                // No company storage counter changes for baseline or promoted assets.
            }
        }
        if (asset.getAssetSource() != DesignDraftAssetSource.BOOTH_BASELINE) {
            assetReferenceService.scheduleCleanup(
                    asset.getPublicId(),
                    resourceType(asset.getAssetType(), asset.getMimeType()));
        }
    }

    private Set<String> draftImageKeys(DesignRequest request) {
        Set<String> keys = new HashSet<>();
        for (DesignDraft draft : request.getDrafts()) {
            for (DesignDraftPanorama panorama : draft.getPanoramas()) {
                keys.add(panorama.getImageKey());
            }
            if (draft.getThumbnailAsset() != null) {
                keys.add(draft.getThumbnailAsset().getPublicId());
            }
            if (draft.getBackgroundMusicAsset() != null) {
                keys.add(draft.getBackgroundMusicAsset().getPublicId());
            }
            draft.getPanoramas().stream()
                    .flatMap(panorama -> panorama.getHotspots().stream())
                    .map(DesignDraftHotspot::getDesignDraftMediaAsset)
                    .filter(media -> media != null && media.getAsset() != null)
                    .map(media -> media.getAsset().getPublicId())
                    .filter(publicId -> publicId != null && !publicId.isBlank())
                    .forEach(keys::add);
        }
        return keys;
    }

    private void pruneUnreferencedDraftMedia(DesignRequest request) {
        pruneUnreferencedDraftMedia(request, null);
    }

    private void pruneUnreferencedDraftMedia(DesignRequest request, UUID targetAssetId) {
        Set<UUID> referencedIds = request.getDrafts().stream()
                .flatMap(draft -> draft.getPanoramas().stream())
                .flatMap(panorama -> panorama.getHotspots().stream())
                .map(DesignDraftHotspot::getDesignDraftMediaAsset)
                .filter(media -> media != null && media.getId() != null)
                .map(DesignDraftMediaAsset::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<DesignDraftMediaAsset> unreferenced = request.getDrafts().stream()
                .filter(draft -> draft.getMediaAssets() != null)
                .flatMap(draft -> draft.getMediaAssets().stream())
                .filter(media -> targetAssetId == null
                        || media.getAsset() != null && targetAssetId.equals(media.getAsset().getId()))
                .filter(media -> media.getId() != null && !referencedIds.contains(media.getId()))
                .toList();
        if (unreferenced.isEmpty()) {
            return;
        }
        Set<UUID> unreferencedIds = unreferenced.stream()
                .map(DesignDraftMediaAsset::getId)
                .collect(java.util.stream.Collectors.toSet());
        request.getDrafts().forEach(draft -> draft.getMediaAssets()
                .removeIf(media -> media.getId() != null && unreferencedIds.contains(media.getId())));
        draftMediaAssetRepository.deleteAll(unreferenced);
        draftMediaAssetRepository.flush();
    }


    private Set<String> boothImageKeys(DesignRequest request) {
        Set<String> keys = new HashSet<>(boothDesignService.getPanoramaImageKeys(request.getBooth().getId()));
        if (request.getBooth().getThumbnailPublicId() != null) {
            keys.add(request.getBooth().getThumbnailPublicId());
        }
        if (request.getBooth().getBackgroundMusicPublicId() != null) {
            keys.add(request.getBooth().getBackgroundMusicPublicId());
        }
        return keys;
    }

    private boolean isReferencedByDraft(DesignRequest request, String publicId) {
        return draftImageKeys(request).contains(publicId);
    }

    private boolean isUsedByBooth(DesignRequest request, String publicId) {
        return boothImageKeys(request).contains(publicId);
    }

    private void requireEditableRequest(DesignRequest request) {
        if (request.getStatus() != DesignRequestStatus.ASSIGNED
                && request.getStatus() != DesignRequestStatus.REVISION_REQUESTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        if (request.getCancellationStatus() == DesignRequestCancellationStatus.REQUESTED) {
            throw new AppException(ErrorCode.DESIGN_CANCELLATION_PENDING);
        }
    }

    private void validateFile(MultipartFile file, DesignDraftAssetType type) {
        if (type == DesignDraftAssetType.PANORAMA) {
            FileUploadUtils.validatePanoramaFile(file);
            return;
        }
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        Set<String> allowedTypes = switch (type) {
            case PANORAMA -> PANORAMA_TYPES;
            case THUMBNAIL -> THUMBNAIL_TYPES;
            case BACKGROUND_MUSIC -> MUSIC_TYPES;
            case MEDIA_ATTACHMENT -> MEDIA_TYPES;
            case MODEL_3D -> Set.of();
        };
        if (file.getContentType() == null
                || !allowedTypes.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    private String resourceType(DesignDraftAssetType type, String mimeType) {
        if (type == DesignDraftAssetType.MEDIA_ATTACHMENT) {
            return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")
                    ? "video"
                    : "image";
        }
        return switch (type) {
            case BACKGROUND_MUSIC -> "video";
            case MODEL_3D -> "raw";
            case PANORAMA, THUMBNAIL -> "image";
            case MEDIA_ATTACHMENT -> throw new IllegalStateException("Handled above");
        };
    }

    private boolean registerRollbackCleanup(String publicId, String resourceType) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    cleanupCloudAsset(publicId, resourceType);
                }
            }
        });
        return true;
    }

    private void cleanupCloudAsset(String publicId, String resourceType) {
        try {
            cloudService.delete(publicId, resourceType);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to clean up rolled-back design asset {}", publicId, cleanupFailure);
        }
    }

    private DesignDraftAssetResponseDTO toResponse(DesignDraftAsset asset) {
        return new DesignDraftAssetResponseDTO(
                asset.getId(),
                asset.getDesignRequest().getId(),
                asset.getUrl(),
                asset.getPublicId(),
                asset.getFileName(),
                asset.getMimeType(),
                asset.getFileSize(),
                asset.getAssetType(),
                asset.getAssetSource(),
                asset.getQuotaState(),
                asset.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public boolean isAssetReferenced(String publicId) {
        return assetRepository.existsByPublicId(publicId);
    }
}
