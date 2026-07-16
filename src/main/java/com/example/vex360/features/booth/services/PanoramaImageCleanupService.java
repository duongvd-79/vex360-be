package com.example.vex360.features.booth.services;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.shared.services.CloudService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PanoramaImageCleanupService {
    private static final String IMAGE_RESOURCE_TYPE = "image";

    private final PanoramaRepository panoramaRepository;
    private final CloudService cloudService;

    public void scheduleCleanup(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        scheduleCleanup(List.of(imageKey));
    }

    public void scheduleCleanup(Collection<String> imageKeys) {
        Set<String> normalizedKeys = normalize(imageKeys);
        if (normalizedKeys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupUnusedImages(normalizedKeys);
                }
            });
            return;
        }
        cleanupUnusedImages(normalizedKeys);
    }

    void cleanupUnusedImages(Set<String> imageKeys) {
        if (imageKeys.size() == 1) {
            String imageKey = imageKeys.iterator().next();
            if (!panoramaRepository.existsByImageKey(imageKey)) {
                deleteImage(imageKey);
            }
            return;
        }
        Set<String> usedKeys = new HashSet<>(panoramaRepository.findUsedImageKeys(imageKeys));
        for (String imageKey : imageKeys) {
            if (usedKeys.contains(imageKey)) {
                continue;
            }
            deleteImage(imageKey);
        }
    }

    private void deleteImage(String imageKey) {
        try {
            cloudService.delete(imageKey, IMAGE_RESOURCE_TYPE);
        } catch (RuntimeException exception) {
            log.warn("Failed to clean up unused panorama image {}", imageKey, exception);
        }
    }

    private Set<String> normalize(Collection<String> imageKeys) {
        Set<String> normalized = new HashSet<>();
        if (imageKeys == null) {
            return normalized;
        }
        for (String imageKey : imageKeys) {
            if (imageKey != null && !imageKey.isBlank()) {
                normalized.add(imageKey);
            }
        }
        return normalized;
    }
}
