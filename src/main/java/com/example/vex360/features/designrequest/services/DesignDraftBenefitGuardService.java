package com.example.vex360.features.designrequest.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftBenefitMetricResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftBenefitUsageResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignDraftBenefitGuardService {
    private final PanoramaRepository panoramaRepository;

    public Usage calculateUsage(DesignDraft draft) {
        if (draft == null || draft.getPanoramas() == null) {
            return Usage.ZERO;
        }

        int hotspotCount = 0;
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> videoIds = new HashSet<>();
        for (DesignDraftPanorama panorama : draft.getPanoramas()) {
            if (panorama == null || panorama.getHotspots() == null) {
                continue;
            }
            hotspotCount += panorama.getHotspots().size();
            for (DesignDraftHotspot hotspot : panorama.getHotspots()) {
                if (hotspot == null) {
                    continue;
                }
                if (hotspot.getProduct() != null && hotspot.getProduct().getId() != null) {
                    productIds.add(hotspot.getProduct().getId());
                }
                MediaAsset mediaAsset = hotspot.getMediaAsset();
                if (mediaAsset != null
                        && mediaAsset.getType() == MediaAssetType.VIDEO
                        && mediaAsset.getId() != null) {
                    videoIds.add(mediaAsset.getId());
                }
            }
        }
        return new Usage(draft.getPanoramas().size(), hotspotCount, productIds.size(), videoIds.size());
    }

    public void assertMutationAllowed(DesignRequest request, Usage beforeUsage, DesignDraft projectedDraft) {
        if (beforeUsage == null || projectedDraft == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        Limits limits = requireLimits(request);
        Usage projected = calculateUsage(projectedDraft);
        boolean allowNoIncrease = request.getMode() == DesignRequestMode.REDESIGN;

        assertMutationMetric(projected.panoramas(), beforeUsage.panoramas(), limits.panoramas(), allowNoIncrease);
        assertMutationMetric(projected.hotspots(), beforeUsage.hotspots(), limits.hotspots(), allowNoIncrease);
        assertMutationMetric(projected.products(), beforeUsage.products(), limits.products(), allowNoIncrease);
        assertMutationMetric(
                projected.embeddedVideos(),
                beforeUsage.embeddedVideos(),
                limits.embeddedVideos(),
                allowNoIncrease);
    }

    public void assertWithinSubmissionLimits(DesignRequest request, DesignDraft draft) {
        if (draft == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        Limits limits = requireLimits(request);
        Usage baseline = calculateBaselineUsage(request);
        Usage working = calculateUsage(draft);
        boolean useBaselineGrace = request.getMode() == DesignRequestMode.REDESIGN;

        assertSubmissionMetric(working.panoramas(), limits.panoramas(), baseline.panoramas(), useBaselineGrace);
        assertSubmissionMetric(working.hotspots(), limits.hotspots(), baseline.hotspots(), useBaselineGrace);
        assertSubmissionMetric(working.products(), limits.products(), baseline.products(), useBaselineGrace);
        assertSubmissionMetric(
                working.embeddedVideos(),
                limits.embeddedVideos(),
                baseline.embeddedVideos(),
                useBaselineGrace);
    }

    public DesignDraftBenefitUsageResponseDTO getUsageResponse(DesignRequest request, DesignDraft draft) {
        Limits limits = requireLimits(request);
        Usage baseline = calculateBaselineUsage(request);
        Usage working = calculateUsage(draft);
        return response(limits, baseline, working);
    }

    public DesignDraftBenefitUsageResponseDTO getBaselineUsageResponse(DesignRequest request) {
        Limits limits = requireLimits(request);
        Usage baseline = calculateBaselineUsage(request);
        return response(limits, baseline, baseline);
    }

    private DesignDraftBenefitUsageResponseDTO response(Limits limits, Usage baseline, Usage working) {
        return new DesignDraftBenefitUsageResponseDTO(
                metric(limits.panoramas(), baseline.panoramas(), working.panoramas()),
                metric(limits.hotspots(), baseline.hotspots(), working.hotspots()),
                metric(limits.products(), baseline.products(), working.products()),
                metric(limits.embeddedVideos(), baseline.embeddedVideos(), working.embeddedVideos()));
    }

    private Usage calculateBaselineUsage(DesignRequest request) {
        Booth booth = requireBooth(request);
        List<Panorama> panoramas = panoramaRepository.findDetailsByBoothId(booth.getId());
        if (panoramas == null) {
            return Usage.ZERO;
        }

        int hotspotCount = 0;
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> videoIds = new HashSet<>();
        for (Panorama panorama : panoramas) {
            if (panorama == null || panorama.getHotspots() == null) {
                continue;
            }
            hotspotCount += panorama.getHotspots().size();
            panorama.getHotspots().forEach(hotspot -> {
                if (hotspot.getProduct() != null && hotspot.getProduct().getId() != null) {
                    productIds.add(hotspot.getProduct().getId());
                }
                MediaAsset mediaAsset = hotspot.getMediaAsset();
                if (mediaAsset != null
                        && mediaAsset.getType() == MediaAssetType.VIDEO
                        && mediaAsset.getId() != null) {
                    videoIds.add(mediaAsset.getId());
                }
            });
        }
        return new Usage(panoramas.size(), hotspotCount, productIds.size(), videoIds.size());
    }

    private Limits requireLimits(DesignRequest request) {
        ExhibitorRegistration registration = requireBooth(request).getExhibitorRegistration();
        if (registration == null) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return new Limits(
                requireLimit(registration.getMaxPanoramasPerBoothSnapshot()),
                requireLimit(registration.getMaxHotspotsPerBoothSnapshot()),
                requireLimit(registration.getMaxProductsPerBoothSnapshot()),
                requireLimit(registration.getMaxEmbeddedVideosPerBoothSnapshot()));
    }

    private Booth requireBooth(DesignRequest request) {
        if (request == null || request.getBooth() == null || request.getBooth().getId() == null) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return request.getBooth();
    }

    private int requireLimit(Integer limit) {
        if (limit == null || limit < 0) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return limit;
    }

    private void assertMutationMetric(int projected, int before, int limit, boolean allowNoIncrease) {
        if (projected <= limit || allowNoIncrease && projected <= before) {
            return;
        }
        throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
    }

    private void assertSubmissionMetric(int working, int limit, int baseline, boolean useBaselineGrace) {
        int allowed = useBaselineGrace ? Math.max(limit, baseline) : limit;
        if (working > allowed) {
            throw new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED);
        }
    }

    private DesignDraftBenefitMetricResponseDTO metric(int limit, int baseline, int working) {
        return new DesignDraftBenefitMetricResponseDTO(
                limit,
                baseline,
                working,
                Math.max(0, limit - working));
    }

    public record Usage(int panoramas, int hotspots, int products, int embeddedVideos) {
        public static final Usage ZERO = new Usage(0, 0, 0, 0);
    }

    private record Limits(int panoramas, int hotspots, int products, int embeddedVideos) {
    }
}
