package com.example.vex360.features.booth.services;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PanoramaImageCleanupService {
    private static final String IMAGE_RESOURCE_TYPE = "image";

    private final DesignAssetReferenceService assetReferenceService;

    public void scheduleCleanup(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        scheduleCleanup(List.of(imageKey));
    }

    public void scheduleCleanup(Collection<String> imageKeys) {
        Set<String> normalizedKeys = normalize(imageKeys);
        assetReferenceService.scheduleCleanup(normalizedKeys, IMAGE_RESOURCE_TYPE);
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
