package com.example.vex360.features.designrequest.services;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;

/**
 * Manages request-scoped panorama assets while a Designer is preparing booth
 * drafts. Uploaded bytes are charged to the Exhibitor company and released
 * when an unused asset is deleted or cleaned up.
 */
@Service
@RequiredArgsConstructor
public class DesignDraftAssetService {
    private static final long MAX_FILE_SIZE = (long) 10 * 1024 * 1024;
    private static final Set<String> PANORAMA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> MUSIC_TYPES = Set.of("audio/mpeg", "audio/mp3");

    private final DesignDraftAssetRepository assetRepository;
    private final DesignerWorkspaceService workspaceService;
    private final CompanyStorageService storageService;
    private final CloudService cloudService;
    private final BoothDesignService boothDesignService;

    /**
     * Uploads a panorama staging asset for an assigned, editable request.
     * Accepted files are JPEG, PNG, or WEBP images up to 10 MB. The uploaded
     * size is added to the Exhibitor company's storage usage; a failed database
     * operation removes the uploaded cloud asset.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param file        panorama image to upload
     * @return metadata used to reference the asset from a draft panorama
     * @throws AppException if assignment, request status, file validation,
     *                      upload, or company quota validation fails
     */
    @Transactional
    public DesignDraftAssetResponseDTO uploadPanorama(User currentUser, UUID requestId, MultipartFile file) {
        return uploadAsset(currentUser, requestId, file, DesignDraftAssetType.PANORAMA);
    }

    /**
     * Uploads a staging asset (panorama, thumbnail, or background music) for an
     * assigned request.
     * Checks company storage quota and performs file validation before uploading to
     * Cloudinary.
     * Deducts company storage quota if subsequent DB save fails.
     *
     * @param currentUser authenticated Designer
     * @param requestId   design request identifier
     * @param file        the file to upload
     * @param assetType   the type of asset (PANORAMA, THUMBNAIL, or
     *                    BACKGROUND_MUSIC)
     * @return the uploaded asset response DTO
     * @throws AppException if verification, quota, or upload fails
     */
    @Transactional
    public DesignDraftAssetResponseDTO uploadAsset(
            User currentUser,
            UUID requestId,
            MultipartFile file,
            DesignDraftAssetType assetType) {
        DesignRequest request = workspaceService.getAssignedRequest(currentUser, requestId);
        requireEditableRequest(request);
        DesignDraftAssetType resolvedType = assetType == null ? DesignDraftAssetType.PANORAMA : assetType;
        validateFile(file, resolvedType);
        storageService.checkQuota(request.getCompany(), file.getSize());

        String folder = switch (resolvedType) {
            case PANORAMA -> FileUploadUtils.PANORAMA_FOLDER;
            case THUMBNAIL -> "design-draft-thumbnail";
            case BACKGROUND_MUSIC -> "design-draft-background-music";
        };
        CloudinaryResponse upload = cloudService.uploadToFolder(file, folder);
        long fileSize = upload.getFileSize() == null ? file.getSize() : upload.getFileSize();
        try {
            if (fileSize != file.getSize()) {
                storageService.checkQuota(request.getCompany(), fileSize);
            }
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
                    .build());
            storageService.addUsage(request.getCompany(), fileSize);
            return toResponse(asset);
        } catch (RuntimeException exception) {
            cloudService.delete(upload.getPublicId(), resourceType(resolvedType));
            throw exception;
        }
    }

    /**
     * Deletes an unused staging asset and deducts its size from company storage.
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
        DesignRequest request = workspaceService.getAssignedRequest(currentUser, requestId);
        requireEditableRequest(request);
        DesignDraftAsset asset = assetRepository.findByIdAndDesignRequestId(assetId, requestId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
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

    /**
     * Removes assets from this request that are not referenced by any saved
     * draft or by the current booth. This is used after draft replacement or
     * submission to release abandoned upload quota.
     *
     * @param request managed request aggregate with its drafts available
     * @return number of assets deleted
     */
    @Transactional
    public int cleanupUnreferencedAssets(DesignRequest request) {
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
            if (!referencedKeys.contains(asset.getPublicId())) {
                deleteAsset(asset);
                deleted++;
            }
        }
        return deleted;
    }

    private void deleteAsset(DesignDraftAsset asset) {
        assetRepository.delete(asset);
        if (asset.getAssetSource() == DesignDraftAssetSource.BOOTH_BASELINE) {
            return;
        }
        storageService.deductUsage(asset.getDesignRequest().getCompany(), asset.getFileSize());
        cloudService.delete(asset.getPublicId(), resourceType(asset.getAssetType()));
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
        }
        return keys;
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
        };
        if (file.getContentType() == null
                || !allowedTypes.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    private String resourceType(DesignDraftAssetType type) {
        return type == DesignDraftAssetType.BACKGROUND_MUSIC ? "video" : "image";
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
                asset.getCreatedAt());
    }
}
