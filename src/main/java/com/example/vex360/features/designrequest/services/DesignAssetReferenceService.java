package com.example.vex360.features.designrequest.services;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes Cloudinary resources only after the surrounding transaction commits
 * and only when neither the official booth nor a retained design draft still
 * references the public ID.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignAssetReferenceService {
    private final BoothDesignService boothDesignService;
    private final DesignDraftPanoramaRepository draftPanoramaRepository;
    private final DesignDraftRepository draftRepository;
    private final CloudService cloudService;
    private final ExhibitorMediaAssetService exhibitorMediaAssetService;
    private final DesignRequestMediaAssetRepository requestMediaAssetRepository;

    @Transactional
    public MediaAssetResponseDTO deleteMediaAsset(User currentUser, UUID assetId) {
        if (requestMediaAssetRepository.existsByMediaAssetIdAndRequestStatusIn(
                assetId,
                DesignRequestRepository.NON_TERMINAL_STATUSES)) {
            throw new AppException(ErrorCode.DESIGN_MEDIA_ASSET_LOCKED);
        }
        MediaAssetResponseDTO response = exhibitorMediaAssetService.deleteMediaAsset(currentUser, assetId);
        scheduleCleanup(response.getPublicId(), toResourceType(response.getType()));
        return response;
    }

    private String toResourceType(MediaAssetType type) {
        return type == MediaAssetType.VIDEO ? "video" : "image";
    }
// check xem imageKeyOld còn tồn tại ở đâu không?
    public boolean isReferenced(String publicId) {
        if (!hasText(publicId)) {
            return false;
        }
        return boothDesignService.existsPanoramaByImageKey(publicId)
                || draftPanoramaRepository.existsByImageKey(publicId)
                || boothDesignService.existsBoothByThumbnailOrMusic(publicId)
                || draftRepository.existsByThumbnailOrBackgroundMusicPublicId(publicId)
                || exhibitorMediaAssetService.existsByPublicId(publicId);
    }

    public void scheduleCleanup(String publicId, String resourceType) {
        if (!hasText(publicId)) {
            return;
        }
        scheduleCleanup(Set.of(publicId), resourceType);
    }

    public void scheduleCleanup(Collection<String> publicIds, String resourceType) {
        Set<String> normalized = normalize(publicIds);
        if (normalized.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> cleanupUnreferenced(normalized, resourceType);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }
        cleanup.run();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCleanupRequested(PanoramaImageCleanupService.CleanupRequested event) {
        cleanupUnreferenced(event.publicIds(), event.resourceType());
    }

    void cleanupUnreferenced(Collection<String> publicIds, String resourceType) {
        for (String publicId : publicIds) {
            if (isReferenced(publicId)) {
                continue;
            }
            try {
                cloudService.delete(publicId, resourceType);
            } catch (RuntimeException exception) {
                log.warn("Failed to clean up unreferenced design asset {}", publicId, exception);
            }
        }
    }

    private Set<String> normalize(Collection<String> publicIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (publicIds == null) {
            return normalized;
        }
        publicIds.stream().filter(this::hasText).forEach(normalized::add);
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
