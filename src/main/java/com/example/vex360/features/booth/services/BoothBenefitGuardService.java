package com.example.vex360.features.booth.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothBenefitGuardService {
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;

    public void assertCanAddPanorama(Booth booth) {
        ExhibitorRegistration registration = requireRegistration(booth);
        Integer limit = requireLimit(registration.getMaxPanoramasPerBoothSnapshot());
        long currentCount = panoramaRepository.countByBoothId(booth.getId());
        assertWithinLimit(currentCount + 1, limit);
    }

    public void assertCanCreateHotspot(Booth booth, Hotspot candidate) {
        ExhibitorRegistration registration = requireRegistration(booth);
        Integer hotspotLimit = requireLimit(registration.getMaxHotspotsPerBoothSnapshot());
        long currentCount = hotspotRepository.countBySourcePanoramaBoothId(booth.getId());
        assertWithinLimit(currentCount + 1, hotspotLimit);
        assertProjectedHotspotResourcesWithinLimits(booth, candidate, null, registration, false);
    }

    public void assertCanUpdateHotspot(Booth booth, Hotspot candidate) {
        ExhibitorRegistration registration = requireRegistration(booth);
        assertProjectedHotspotResourcesWithinLimits(booth, candidate, candidate.getId(), registration, true);
    }

    private void assertProjectedHotspotResourcesWithinLimits(
            Booth booth,
            Hotspot candidate,
            UUID excludedHotspotId,
            ExhibitorRegistration registration,
            boolean allowNoIncrease) {
        assertProjectedProductCountWithinLimit(booth, candidate.getProduct(), excludedHotspotId,
                requireLimit(registration.getMaxProductsPerBoothSnapshot()), allowNoIncrease);
        assertProjectedVideoCountWithinLimit(booth, candidate.getMediaAsset(), excludedHotspotId,
                requireLimit(registration.getMaxEmbeddedVideosPerBoothSnapshot()), allowNoIncrease);
        assertProjectedStorageWithinLimit(booth, candidate.getMediaAsset(), excludedHotspotId,
                requireStorageLimitBytes(registration.getStorageLimitMbSnapshot()), allowNoIncrease);
    }

    private void assertProjectedProductCountWithinLimit(
            Booth booth,
            Product candidateProduct,
            UUID excludedHotspotId,
            Integer limit,
            boolean allowNoIncrease) {
        Set<UUID> productIds = new HashSet<>(
                hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), excludedHotspotId));
        if (candidateProduct != null && candidateProduct.getId() != null) {
            productIds.add(candidateProduct.getId());
        }
        if (isAllowed(productIds.size(), limit, allowNoIncrease,
                () -> hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), null).size())) {
            return;
        }
        throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
    }

    private void assertProjectedVideoCountWithinLimit(
            Booth booth,
            MediaAsset candidateMediaAsset,
            UUID excludedHotspotId,
            Integer limit,
            boolean allowNoIncrease) {
        List<MediaAsset> mediaAssets = hotspotRepository
                .findDistinctMediaAssetsByBoothIdExcludingHotspot(booth.getId(), excludedHotspotId);
        Set<UUID> videoIds = new HashSet<>();
        for (MediaAsset mediaAsset : mediaAssets) {
            if (mediaAsset.getType() == MediaAssetType.VIDEO && mediaAsset.getId() != null) {
                videoIds.add(mediaAsset.getId());
            }
        }
        if (candidateMediaAsset != null
                && candidateMediaAsset.getType() == MediaAssetType.VIDEO
                && candidateMediaAsset.getId() != null) {
            videoIds.add(candidateMediaAsset.getId());
        }
        if (isAllowed(videoIds.size(), limit, allowNoIncrease,
                () -> countVideos(hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(
                        booth.getId(), null)))) {
            return;
        }
        throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
    }

    private void assertProjectedStorageWithinLimit(
            Booth booth,
            MediaAsset candidateMediaAsset,
            UUID excludedHotspotId,
            Long limitBytes,
            boolean allowNoIncrease) {
        List<MediaAsset> mediaAssets = hotspotRepository
                .findDistinctMediaAssetsByBoothIdExcludingHotspot(booth.getId(), excludedHotspotId);
        long projectedBytes = sumStorageBytes(mediaAssets, candidateMediaAsset);
        if (isAllowed(projectedBytes, limitBytes, allowNoIncrease,
                () -> sumStorageBytes(hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(
                        booth.getId(), null), null))) {
            return;
        }
        throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
    }

    private int countVideos(List<MediaAsset> mediaAssets) {
        Set<UUID> videoIds = new HashSet<>();
        if (mediaAssets == null) {
            return 0;
        }
        for (MediaAsset mediaAsset : mediaAssets) {
            if (mediaAsset.getType() == MediaAssetType.VIDEO && mediaAsset.getId() != null) {
                videoIds.add(mediaAsset.getId());
            }
        }
        return videoIds.size();
    }

    private long sumStorageBytes(List<MediaAsset> mediaAssets, MediaAsset candidateMediaAsset) {
        Set<UUID> mediaAssetIds = new HashSet<>();
        long totalBytes = 0L;
        if (mediaAssets != null) {
            for (MediaAsset mediaAsset : mediaAssets) {
                if (mediaAsset.getId() == null || mediaAssetIds.add(mediaAsset.getId())) {
                    totalBytes += safeFileSize(mediaAsset);
                }
            }
        }
        if (candidateMediaAsset != null
                && (candidateMediaAsset.getId() == null || mediaAssetIds.add(candidateMediaAsset.getId()))) {
            totalBytes += safeFileSize(candidateMediaAsset);
        }
        return totalBytes;
    }

    private boolean isAllowed(long projected, long limit, boolean allowNoIncrease, UsageSupplier currentUsageSupplier) {
        if (projected <= limit) {
            return true;
        }
        return allowNoIncrease && projected <= currentUsageSupplier.get();
    }

    private ExhibitorRegistration requireRegistration(Booth booth) {
        if (booth == null || booth.getId() == null || booth.getExhibitorRegistration() == null) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return booth.getExhibitorRegistration();
    }

    private Integer requireLimit(Integer limit) {
        if (limit == null || limit < 0) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return limit;
    }

    private Long requireStorageLimitBytes(Long limitMb) {
        if (limitMb == null || limitMb < 0) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return limitMb * BYTES_PER_MB;
    }

    private long safeFileSize(MediaAsset mediaAsset) {
        return mediaAsset.getFileSize() == null ? 0L : mediaAsset.getFileSize();
    }

    private void assertWithinLimit(long projected, long limit) {
        if (projected > limit) {
            throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
        }
    }

    @FunctionalInterface
    private interface UsageSupplier {
        long get();
    }
}
