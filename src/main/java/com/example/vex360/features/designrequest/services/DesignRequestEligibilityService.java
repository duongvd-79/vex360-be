package com.example.vex360.features.designrequest.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestScopeAvailabilityDTO;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignRequestScope;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Service responsible for evaluating and enforcing eligibility rules for Booth
 * Design Requests.
 * Checks factors like remaining action quota, request mode
 * (INITIAL_DESIGN/REDESIGN), scope,
 * active requests, and product states.
 */
@Service
@RequiredArgsConstructor
public class DesignRequestEligibilityService {
    public static final int MAX_DESIGN_ACTIONS = 3;
    public static final String BOOTH_NOT_DRAFT = "BOOTH_NOT_DRAFT";
    public static final String ACTIVE_REQUEST_EXISTS = "ACTIVE_REQUEST_EXISTS";
    public static final String ACTION_QUOTA_EXHAUSTED = "ACTION_QUOTA_EXHAUSTED";
    public static final String NON_SPATIAL_HOTSPOTS = "NON_SPATIAL_HOTSPOTS";
    public static final String INACTIVE_BASELINE_PRODUCT = "INACTIVE_BASELINE_PRODUCT";

    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final DesignRequestRepository designRequestRepository;

    /**
     * Evaluates a booth to determine its eligibility for creating a new design
     * request.
     * Evaluates available scopes (SPATIAL, FULL) and checks for any blocking
     * reasons.
     *
     * @param booth the booth to evaluate
     * @return the design request eligibility details response DTO
     */
    @Transactional(readOnly = true)
    public DesignRequestEligibilityResponseDTO evaluate(Booth booth) {
        DesignRequestMode mode = inferMode(booth);
        int remaining = remainingActions(booth);
        String requestReason = requestReason(booth, remaining);

        boolean spatialAvailable = mode == DesignRequestMode.INITIAL_DESIGN
                || !hotspotRepository.existsBySourcePanoramaBoothIdAndTypeNot(booth.getId(), HotspotType.NAV);
        boolean fullAvailable = mode == DesignRequestMode.INITIAL_DESIGN
                || !hotspotRepository.existsBySourcePanoramaBoothIdAndProductStatusNot(
                        booth.getId(), ProductStatus.ACTIVE);

        List<DesignRequestScopeAvailabilityDTO> scopes = List.of(
                new DesignRequestScopeAvailabilityDTO(
                        DesignRequestScope.SPATIAL,
                        spatialAvailable,
                        spatialAvailable ? null : NON_SPATIAL_HOTSPOTS),
                new DesignRequestScopeAvailabilityDTO(
                        DesignRequestScope.FULL,
                        fullAvailable,
                        fullAvailable ? null : INACTIVE_BASELINE_PRODUCT));
        return new DesignRequestEligibilityResponseDTO(
                booth.getId(), mode, requestReason == null, requestReason, remaining, scopes);
    }

    /**
     * Asserts that a design request can be created for the given scope based on
     * evaluated eligibility.
     * Throws an exception if not eligible.
     *
     * @param eligibility the evaluated eligibility details
     * @param scope       the target scope of the design request (SPATIAL, FULL)
     * @throws AppException if quota is exceeded or the requested scope is
     *                      unavailable
     */
    public void assertCanCreate(DesignRequestEligibilityResponseDTO eligibility, DesignRequestScope scope) {
        if (!eligibility.isEligible()) {
            throw eligibility.getRemainingDesignActions() == 0
                    ? new AppException(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED)
                    : new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
        boolean available = eligibility.getScopes().stream()
                .anyMatch(candidate -> candidate.getScope() == scope && candidate.isAvailable());
        if (!available) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
    }

    /**
     * Asserts that a pending design request can be assigned to a Designer.
     * Validates booth status, mode mismatch, and existence of non-spatial hotspots
     * or inactive products.
     *
     * @param request the design request to check
     * @throws AppException if the request is not assignable
     */
    public void assertCanAssign(DesignRequest request) {
        Booth booth = request.getBooth();
        if (booth.getStatus() != BoothStatus.DESIGN_REQUEST_PENDING
                || inferMode(booth) != request.getMode()) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
        if (request.getScope() == DesignRequestScope.SPATIAL
                && request.getMode() == DesignRequestMode.REDESIGN
                && hotspotRepository.existsBySourcePanoramaBoothIdAndTypeNot(booth.getId(), HotspotType.NAV)) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
        if (request.getScope() == DesignRequestScope.FULL
                && request.getMode() == DesignRequestMode.REDESIGN
                && hotspotRepository.existsBySourcePanoramaBoothIdAndProductStatusNot(
                        booth.getId(), ProductStatus.ACTIVE)) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
    }

    /**
     * Infers the design request mode for a booth based on the presence of
     * panoramas.
     *
     * @param booth the booth to check
     * @return INITIAL_DESIGN if there are no panoramas, otherwise REDESIGN
     */
    public DesignRequestMode inferMode(Booth booth) {
        return panoramaRepository.countByBoothId(booth.getId()) == 0
                ? DesignRequestMode.INITIAL_DESIGN
                : DesignRequestMode.REDESIGN;
    }

    /**
     * Calculates the remaining design actions allowed for a booth.
     *
     * @param booth the booth to check
     * @return the number of remaining design actions
     */
    public int remainingActions(Booth booth) {
        long used = designRequestRepository.countByBoothIdAndQuotaChargedTrue(booth.getId())
                + designRequestRepository.sumReviewCountByBoothId(booth.getId());
        return (int) Math.max(0, MAX_DESIGN_ACTIONS - used);
    }

    private String requestReason(Booth booth, int remaining) {
        if (booth.getStatus() != BoothStatus.DRAFT) {
            return BOOTH_NOT_DRAFT;
        }
        if (designRequestRepository.existsByBoothIdAndStatusIn(
                booth.getId(), DesignRequestRepository.NON_TERMINAL_STATUSES)) {
            return ACTIVE_REQUEST_EXISTS;
        }
        return remaining == 0 ? ACTION_QUOTA_EXHAUSTED : null;
    }
}
