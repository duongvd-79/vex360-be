package com.example.vex360.features.assetcleanup.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.vex360.features.assetcleanup.services.CloudAssetReferenceService;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService.Asset;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService.AssetPage;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.LogSanitizer;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(prefix = "app.cloudinary.orphan-cleanup", name = "enabled", havingValue = "true")
@Slf4j
public class CloudinaryOrphanCleanupJob {

    private static final List<Scope> OWNED_SCOPES = List.of(
            new Scope("image/", "image"),
            new Scope("panorama/", "image"),
            new Scope("avatar/", "image"),
            new Scope("video/", "video"),
            new Scope("audio/", "video"),
            new Scope("booth-background-music/", "video"));

    private final CloudinaryAssetInventoryService inventoryService;
    private final CloudAssetReferenceService referenceService;
    private final CloudService cloudService;
    private final Clock clock;
    private final Duration gracePeriod;
    private final int pageSize;
    private final int maxScanned;
    private final int maxDeletions;

    public CloudinaryOrphanCleanupJob(
            CloudinaryAssetInventoryService inventoryService,
            CloudAssetReferenceService referenceService,
            CloudService cloudService,
            Clock clock,
            @Value("${app.cloudinary.orphan-cleanup.grace-period:PT24H}") Duration gracePeriod,
            @Value("${app.cloudinary.orphan-cleanup.page-size:100}") int pageSize,
            @Value("${app.cloudinary.orphan-cleanup.max-scanned-per-run:1000}") int maxScanned,
            @Value("${app.cloudinary.orphan-cleanup.max-deletions-per-run:100}") int maxDeletions) {
        if (gracePeriod == null || gracePeriod.isZero() || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("Cloudinary orphan cleanup grace period must be positive");
        }
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("Cloudinary orphan cleanup page size must be between 1 and 500");
        }
        if (maxScanned < 1) {
            throw new IllegalArgumentException("Cloudinary orphan cleanup scan limit must be positive");
        }
        if (maxDeletions < 1) {
            throw new IllegalArgumentException("Cloudinary orphan cleanup deletion limit must be positive");
        }
        this.inventoryService = inventoryService;
        this.referenceService = referenceService;
        this.cloudService = cloudService;
        this.clock = clock;
        this.gracePeriod = gracePeriod;
        this.pageSize = pageSize;
        this.maxScanned = maxScanned;
        this.maxDeletions = maxDeletions;
    }

    @Scheduled(cron = "${app.cloudinary.orphan-cleanup.cron:0 30 2 * * *}", zone = "UTC")
    public void cleanupOrphans() {
        Instant cutoff = Instant.now(clock).minus(gracePeriod);
        int scanned = 0;
        int deleted = 0;

        for (Scope scope : OWNED_SCOPES) {
            if (scanned >= maxScanned || deleted >= maxDeletions) {
                break;
            }

            String cursor = null;
            try {
                do {
                    AssetPage page = inventoryService.list(scope.prefix(), scope.resourceType(), cursor, pageSize);
                    for (Asset asset : page.assets()) {
                        if (scanned >= maxScanned || deleted >= maxDeletions) {
                            break;
                        }
                        scanned++;
                        if (asset.createdAt().isAfter(cutoff) || referenceService.isReferenced(asset.publicId())) {
                            continue;
                        }
                        try {
                            cloudService.delete(asset.publicId(), asset.resourceType());
                            deleted++;
                        } catch (RuntimeException exception) {
                            log.warn("Failed to delete orphan Cloudinary asset {}",
                                    LogSanitizer.sanitize(asset.publicId()), exception);
                        }
                    }

                    if (scanned >= maxScanned || deleted >= maxDeletions) {
                        break;
                    }
                    cursor = page.nextCursor();
                } while (cursor != null && !cursor.isBlank());
            } catch (RuntimeException exception) {
                log.warn("Failed to scan Cloudinary scope {}/{}",
                        scope.resourceType(), scope.prefix(), exception);
            }
        }

        log.info("Cloudinary orphan cleanup completed: scanned={}, deleted={}", scanned, deleted);
    }

    private record Scope(String prefix, String resourceType) {
    }
}
