package com.example.vex360.features.booth;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService.CleanupRequested;

@ExtendWith(MockitoExtension.class)
class PanoramaImageCleanupServiceUnitTest {
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PanoramaImageCleanupService service;

    @BeforeEach
    void setup() {
        service = new PanoramaImageCleanupService(eventPublisher);
    }

    @Test
    void scheduleCleanupDeletesOnlyUnusedImageKeys() {
        Set<String> keys = Set.of("shared-key", "unused-key");
        service.scheduleCleanup(keys);

        verify(eventPublisher).publishEvent(new CleanupRequested(keys, "image"));
    }

    @Test
    void scheduleCleanupIgnoresBlankKeys() {
        service.scheduleCleanup(List.of("", "   "));

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scheduleCleanupRetainsSingleUsedImageKey() {
        service.scheduleCleanup("shared-key");

        verify(eventPublisher).publishEvent(new CleanupRequested(Set.of("shared-key"), "image"));
    }
}
