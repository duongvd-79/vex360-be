package com.example.vex360.features.booth.services;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PanoramaImageCleanupService {
    private static final String IMAGE_RESOURCE_TYPE = "image";

    private final ApplicationEventPublisher eventPublisher;

    public record CleanupRequested(Set<String> publicIds, String resourceType) {
    }

    public void scheduleCleanup(String imageKey) {
        scheduleCleanup(imageKey, IMAGE_RESOURCE_TYPE);
    }

    public void scheduleCleanup(Collection<String> imageKeys) {
        scheduleCleanup(imageKeys, IMAGE_RESOURCE_TYPE);
    }

    public void scheduleCleanup(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        scheduleCleanup(List.of(publicId), resourceType);
    }

    public void scheduleCleanup(Collection<String> publicIds, String resourceType) {
        Set<String> normalizedKeys = normalize(publicIds);
        if (!normalizedKeys.isEmpty()) {
            eventPublisher.publishEvent(new CleanupRequested(normalizedKeys, resourceType));
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
