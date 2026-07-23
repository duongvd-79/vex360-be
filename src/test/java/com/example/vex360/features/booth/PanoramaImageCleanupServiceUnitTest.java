package com.example.vex360.features.booth;

import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;

@ExtendWith(MockitoExtension.class)
class PanoramaImageCleanupServiceUnitTest {
    @Mock
    private DesignAssetReferenceService assetReferenceService;

    private PanoramaImageCleanupService service;

    @BeforeEach
    void setup() {
        service = new PanoramaImageCleanupService(assetReferenceService);
    }

    @Test
    void scheduleCleanupDeletesOnlyUnusedImageKeys() {
        Set<String> keys = Set.of("shared-key", "unused-key");
        service.scheduleCleanup(keys);

        verify(assetReferenceService).scheduleCleanup(keys, "image");
    }

    @Test
    void scheduleCleanupIgnoresBlankKeys() {
        service.scheduleCleanup(List.of("", "   "));

        verify(assetReferenceService).scheduleCleanup(Set.of(), "image");
    }

    @Test
    void scheduleCleanupRetainsSingleUsedImageKey() {
        service.scheduleCleanup("shared-key");

        verify(assetReferenceService).scheduleCleanup(Set.of("shared-key"), "image");
    }
}
