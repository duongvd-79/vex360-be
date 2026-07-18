package com.example.vex360.features.booth;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class PanoramaImageCleanupServiceUnitTest {
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private CloudService cloudService;

    private PanoramaImageCleanupService service;

    @BeforeEach
    void setup() {
        service = new PanoramaImageCleanupService(panoramaRepository, cloudService);
    }

    @Test
    void scheduleCleanupDeletesOnlyUnusedImageKeys() {
        Set<String> keys = Set.of("shared-key", "unused-key");
        when(panoramaRepository.findUsedImageKeys(keys)).thenReturn(List.of("shared-key"));

        service.scheduleCleanup(keys);

        verify(cloudService, never()).delete("shared-key", "image");
        verify(cloudService).delete("unused-key", "image");
    }

    @Test
    void scheduleCleanupIgnoresBlankKeys() {
        service.scheduleCleanup(List.of("", "   "));

        verify(panoramaRepository, never()).findUsedImageKeys(org.mockito.ArgumentMatchers.anySet());
        verify(cloudService, never()).delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scheduleCleanupRetainsSingleUsedImageKey() {
        when(panoramaRepository.existsByImageKey("shared-key")).thenReturn(true);

        service.scheduleCleanup("shared-key");

        verify(cloudService, never()).delete("shared-key", "image");
    }
}
