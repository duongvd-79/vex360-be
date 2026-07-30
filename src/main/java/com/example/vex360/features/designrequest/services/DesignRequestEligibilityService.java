package com.example.vex360.features.designrequest.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Service responsible for evaluating and enforcing eligibility rules for Booth
 * Design Requests.
 * Checks factors like remaining action quota, request mode
 * (INITIAL_DESIGN/REDESIGN),
 * active requests, and product states.
 */
@Service
@RequiredArgsConstructor
public class DesignRequestEligibilityService {
    public static final int MAX_DESIGN_ACTIONS = 3;
    public static final String BOOTH_NOT_DRAFT = "BOOTH_NOT_DRAFT";
    public static final String ACTIVE_REQUEST_EXISTS = "ACTIVE_REQUEST_EXISTS";
    public static final String ACTION_QUOTA_EXHAUSTED = "ACTION_QUOTA_EXHAUSTED";
    public static final String INACTIVE_BASELINE_PRODUCT = "INACTIVE_BASELINE_PRODUCT";

    private final BoothDesignService boothDesignService;
    private final DesignRequestRepository designRequestRepository;

    /**
     * Evaluates a booth to determine its eligibility for creating a new design
     * request.
     * Checks whether the booth can enter the full design workflow.
     *
     * @param booth the booth to evaluate
     * @return the design request eligibility details response DTO
     */
    @Transactional(readOnly = true)
    public DesignRequestEligibilityResponseDTO evaluate(Booth booth) {
        DesignRequestMode mode = inferMode(booth);
        int remaining = remainingActions(booth);
        String requestReason = requestReason(booth, mode, remaining);
        return new DesignRequestEligibilityResponseDTO(
                booth.getId(), mode, requestReason == null, requestReason, remaining);
    }

    /**
     * Asserts that a design request can be created based on evaluated eligibility.
     * Throws an exception if not eligible.
     *
     * @param eligibility the evaluated eligibility details
     * @throws AppException if quota is exceeded or the request is unavailable
     */
    public void assertCanCreate(DesignRequestEligibilityResponseDTO eligibility) {
        if (!eligibility.isEligible()) {
            throw eligibility.getRemainingDesignActions() == 0
                    ? new AppException(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED)
                    : new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }
    }

    /**
     * Asserts that a pending design request can be assigned to a Designer.
     * Validates booth status, mode consistency, and baseline product state.
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
        if (request.getMode() == DesignRequestMode.REDESIGN
                && boothDesignService.existsInactiveHotspotProductInBooth(booth.getId())) {
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
        return boothDesignService.countPanoramasByBoothId(booth.getId()) == 0
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

    private String requestReason(Booth booth, DesignRequestMode mode, int remaining) {
        if (booth.getStatus() != BoothStatus.DRAFT) {
            return BOOTH_NOT_DRAFT;
        }
        if (designRequestRepository.existsByBoothIdAndStatusIn(
                booth.getId(), DesignRequestRepository.NON_TERMINAL_STATUSES)) {
            return ACTIVE_REQUEST_EXISTS;
        }
        if (remaining == 0) {
            return ACTION_QUOTA_EXHAUSTED;
        }
        if (mode == DesignRequestMode.REDESIGN
                && boothDesignService.existsInactiveHotspotProductInBooth(booth.getId())) {
            return INACTIVE_BASELINE_PRODUCT;
        }
        return null;
    }
}
