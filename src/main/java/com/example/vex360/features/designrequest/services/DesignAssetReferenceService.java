package com.example.vex360.features.designrequest.services;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
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
    private final PanoramaRepository panoramaRepository;
    private final BoothRepository boothRepository;
    private final DesignDraftPanoramaRepository draftPanoramaRepository;
    private final DesignDraftRepository draftRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CloudService cloudService;

    public boolean isReferenced(String publicId) {
        if (!hasText(publicId)) {
            return false;
        }
        return panoramaRepository.existsByImageKey(publicId)
                || draftPanoramaRepository.existsByImageKey(publicId)
                || boothRepository.existsByThumbnailPublicIdOrBackgroundMusicPublicId(publicId, publicId)
                || draftRepository.existsByThumbnailOrBackgroundMusicPublicId(publicId)
                || mediaAssetRepository.existsByPublicId(publicId);
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
