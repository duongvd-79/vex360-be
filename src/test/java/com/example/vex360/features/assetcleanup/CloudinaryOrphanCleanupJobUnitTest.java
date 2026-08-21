package com.example.vex360.features.assetcleanup;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.assetcleanup.jobs.CloudinaryOrphanCleanupJob;
import com.example.vex360.features.assetcleanup.services.CloudAssetReferenceService;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService.Asset;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService.AssetPage;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class CloudinaryOrphanCleanupJobUnitTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    private CloudinaryAssetInventoryService inventoryService;

    @Mock
    private CloudAssetReferenceService referenceService;

    @Mock
    private CloudService cloudService;

    private CloudinaryOrphanCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new CloudinaryOrphanCleanupJob(
                inventoryService,
                referenceService,
                cloudService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24),
                100,
                1000,
                100);
        lenient().when(inventoryService.list(anyString(), anyString(), isNull(), eq(100)))
                .thenReturn(new AssetPage(List.of(), null));
    }

    @Test
    void deletesOnlyOldUnreferencedAssets() {
        Asset orphan = new Asset("image/old-orphan", "image", NOW.minus(Duration.ofDays(2)));
        Asset referenced = new Asset("image/referenced", "image", NOW.minus(Duration.ofDays(2)));
        Asset fresh = new Asset("image/fresh", "image", NOW.minus(Duration.ofHours(1)));

        when(inventoryService.list("image/", "image", null, 100))
                .thenReturn(new AssetPage(List.of(orphan, referenced, fresh), null));
        when(referenceService.isReferenced(orphan.publicId())).thenReturn(false);
        when(referenceService.isReferenced(referenced.publicId())).thenReturn(true);

        job.cleanupOrphans();

        verify(cloudService).delete(orphan.publicId(), orphan.resourceType());
        verify(cloudService, never()).delete(referenced.publicId(), referenced.resourceType());
        verify(cloudService, never()).delete(fresh.publicId(), fresh.resourceType());
        verify(referenceService, never()).isReferenced(fresh.publicId());
    }

    @Test
    void scansEveryDesignDraftAssetScope() {
        job.cleanupOrphans();

        verify(inventoryService).list("design-draft-thumbnail/", "image", null, 100);
        verify(inventoryService).list("design-draft-background-music/", "video", null, 100);
        verify(inventoryService).list("design-draft-media-attachment/", "image", null, 100);
        verify(inventoryService).list("design-draft-media-attachment/", "video", null, 100);
        verify(inventoryService).list("design-draft-model-3d/", "raw", null, 100);
    }

    @Test
    void followsPaginationWithinSameRun() {
        Asset referenced = new Asset("image/referenced", "image", NOW.minus(Duration.ofDays(2)));
        Asset orphan = new Asset("image/orphan-on-second-page", "image", NOW.minus(Duration.ofDays(2)));

        when(inventoryService.list("image/", "image", null, 100))
                .thenReturn(new AssetPage(List.of(referenced), "next-page"));
        when(inventoryService.list("image/", "image", "next-page", 100))
                .thenReturn(new AssetPage(List.of(orphan), null));
        when(referenceService.isReferenced(referenced.publicId())).thenReturn(true);

        job.cleanupOrphans();

        verify(inventoryService).list("image/", "image", "next-page", 100);
        verify(cloudService).delete(orphan.publicId(), orphan.resourceType());
    }

    @Test
    void stopsAtScanLimit() {
        Asset referenced = new Asset("image/referenced", "image", NOW.minus(Duration.ofDays(2)));
        job = new CloudinaryOrphanCleanupJob(
                inventoryService,
                referenceService,
                cloudService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24),
                100,
                1,
                100);

        when(inventoryService.list("image/", "image", null, 100))
                .thenReturn(new AssetPage(List.of(referenced), "next-page"));
        when(referenceService.isReferenced(referenced.publicId())).thenReturn(true);

        job.cleanupOrphans();

        verify(inventoryService, never()).list("image/", "image", "next-page", 100);
    }

    @Test
    void doesNotAdvanceCursorWhenDeletionLimitInterruptsPage() {
        Asset first = new Asset("image/first", "image", NOW.minus(Duration.ofDays(2)));
        Asset second = new Asset("image/second", "image", NOW.minus(Duration.ofDays(2)));
        job = new CloudinaryOrphanCleanupJob(
                inventoryService,
                referenceService,
                cloudService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24),
                100,
                1000,
                1);

        when(inventoryService.list("image/", "image", null, 100))
                .thenReturn(new AssetPage(List.of(first, second), "next-page"));

        job.cleanupOrphans();
        job.cleanupOrphans();

        verify(inventoryService, org.mockito.Mockito.times(2))
                .list("image/", "image", null, 100);
    }
}
