package com.example.vex360.features.designrequest.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.BoothDesignService.HotspotDesign;
import com.example.vex360.features.booth.services.BoothDesignService.PanoramaDesign;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.designrequest.dtos.request.AssignDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.RejectDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignAssignmentAnalyticsResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkloadResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.events.DesignRequestStatusChangedEvent;
import com.example.vex360.features.designrequest.events.DesignRequestCancellationChangedEvent;
import com.example.vex360.features.designrequest.events.DesignRequestRevisionPromotedEvent;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignRequestScope;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.features.designrequest.dtos.response.DesignAssignmentCandidateResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates the complete booth design-request lifecycle across Exhibitor,
 * Admin, and Designer roles. The service enforces status transitions, booth
 * action quota, Designer workload, draft validation, booth locking, and
 * publication of status-change events.
 */
@Service
@RequiredArgsConstructor
public class DesignRequestService {
    private static final int MAX_ACTIVE_REQUESTS_PER_DESIGNER = 3;

    private final DesignRequestRepository designRequestRepository;
    private final DesignDraftRepository designDraftRepository;
    private final DesignRequestMapper designRequestMapper;
    private final BoothDesignService boothDesignService;
    private final CompanyService companyService;
    private final UserService userService;
    private final ProductService productService;
    private final DesignRequestEligibilityService eligibilityService;
    private final DesignRequestProductService requestProductService;
    private final DesignRequestBaselineService baselineService;
    private final DesignDraftAssetService designDraftAssetService;
    private final DesignDraftSettingsService draftSettingsService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    /**
     * Creates a pending design request for an Exhibitor-owned booth. The booth
     * must be in DRAFT and have remaining design-action quota. On success the
     * booth moves to DESIGN_REQUEST_PENDING; spatial content is locked while
     * metadata, files, and the allowlist remain editable until assignment.
     *
     * @param currentUser authenticated Exhibitor
     * @param request     booth identifier and optional design note
     * @return the newly created pending request
     * @throws AppException if authentication, company ownership, booth status,
     *                      or booth design-action quota validation fails
     */
    @Transactional
    public DesignRequestResponseDTO createRequest(User currentUser, CreateDesignRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getCompanyBoothForUpdate(request.getBoothId(), company);
        var eligibility = eligibilityService.evaluate(booth);
        eligibilityService.assertCanCreate(eligibility, request.getScope());
        booth.setStatus(BoothStatus.DESIGN_REQUEST_PENDING);

        DesignRequest designRequest = DesignRequest.builder()
                .booth(booth)
                .company(company)
                .requestedBy(currentUser)
                .note(trimToNull(request.getNote()))
                .status(DesignRequestStatus.PENDING)
                .mode(eligibility.getMode())
                .scope(request.getScope())
                .reviewCount(0)
                .build();
        requestProductService.initializeAllowlist(designRequest, request.getProductIds());
        DesignRequest saved = designRequestRepository.save(designRequest);
        publishStatusChanged(saved, currentUser, null);
        return toResponse(saved);
    }

    /**
     * Evaluates request mode, available scopes, blocking reason, and remaining
     * action quota for an Exhibitor-owned booth.
     */
    @Transactional(readOnly = true)
    public DesignRequestEligibilityResponseDTO getEligibility(
            User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothDesignService.getCompanyBooth(boothId, company.getId());
        return eligibilityService.evaluate(booth);
    }

    /**
     * Replaces optional product visibility while a FULL request is PENDING;
     * products required by a redesign baseline are always retained.
     */
    @Transactional
    public DesignRequestResponseDTO updatePendingProducts(
            User currentUser,
            UUID id,
            List<UUID> productIds) {
        Company company = getCompanyForCurrentUser(currentUser);
        DesignRequest request = getRequestForCompany(id, company);
        if (request.getStatus() != DesignRequestStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        requestProductService.replaceOptionalProducts(request, productIds);
        return toResponse(designRequestRepository.save(request));
    }

    /**
     * Lists design requests belonging to the authenticated Exhibitor company.
     *
     * @param currentUser authenticated Exhibitor
     * @param status      optional request-status filter
     * @param pageable    pagination and sorting options
     * @return a page of company design requests
     * @throws AppException if the user is unauthenticated or has no company
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestResponseDTO> getRequestsForExhibitor(
            User currentUser,
            DesignRequestStatus status,
            Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<DesignRequestResponseDTO> page = designRequestRepository
                .searchForCompany(company.getId(), status, pageable)
                .map(this::toResponse);
        return PageResponse.from(page);
    }

    /**
     * Lists all design requests for Admin management, optionally filtered by
     * request status and assigned Designer.
     *
     * @param status     optional request-status filter
     * @param designerId optional assigned Designer identifier
     * @param pageable   pagination and sorting options
     * @return a page of matching design requests
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestResponseDTO> getRequestsForAdmin(
            DesignRequestStatus status,
            UUID designerId,
            DesignRequestMode mode,
            DesignRequestScope scope,
            Pageable pageable) {
        return PageResponse.from(designRequestRepository.searchForAdmin(status, designerId, mode, scope, pageable)
                .map(this::toResponse));
    }

    /**
     * Lists requests assigned to the authenticated Designer.
     *
     * @param currentUser authenticated Designer
     * @param status      optional request-status filter
     * @param pageable    pagination and sorting options
     * @return a page of requests assigned to the Designer
     * @throws AppException if the user is unauthenticated
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestResponseDTO> getRequestsForDesigner(
            User currentUser,
            DesignRequestStatus status,
            Pageable pageable) {
        User designer = requireCurrentUser(currentUser);
        return PageResponse.from(designRequestRepository.searchForDesigner(designer.getId(), status, pageable)
                .map(this::toResponse));
    }

    /**
     * Cancels an Exhibitor request that is still pending assignment. The booth
     * returns to DRAFT and the charged create action is refunded.
     *
     * @param currentUser authenticated Exhibitor that owns the request company
     * @param id          design request identifier
     * @return the canceled request
     * @throws AppException if the request is missing, belongs to another
     *                      company, or is no longer PENDING
     */
    @Transactional
    public DesignRequestResponseDTO cancelRequest(User currentUser, UUID id) {
        Company company = getCompanyForCurrentUser(currentUser);
        DesignRequest request = getRequestForCompany(id, company);
        if (request.getStatus() != DesignRequestStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        DesignRequestStatus previousStatus = request.getStatus();
        request.setStatus(DesignRequestStatus.CANCELED);
        request.setQuotaCharged(false);
        request.setCanceledAt(LocalDateTime.now());
        request.getBooth().setStatus(BoothStatus.DRAFT);
        DesignRequest saved = designRequestRepository.save(request);
        publishStatusChanged(saved, currentUser, previousStatus);
        return toResponse(saved);
    }

    /**
     * Records an Exhibitor cancellation request after assignment without
     * changing the main workflow status or releasing a working slot.
     */
    @Transactional
    public DesignRequestResponseDTO requestCancellation(User currentUser, UUID id, String reason) {
        Company company = getCompanyForCurrentUser(currentUser);
        DesignRequest request = getRequestForCompany(id, company);
        if (request.getStatus() == DesignRequestStatus.PENDING) {
            return cancelRequest(currentUser, id);
        }
        if (request.getStatus() == DesignRequestStatus.APPROVED
                || request.getStatus() == DesignRequestStatus.CANCELED
                || request.getCancellationStatus() == DesignRequestCancellationStatus.REQUESTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        String cancellationReason = trimToNull(reason);
        if (cancellationReason == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        request.setCancellationStatus(DesignRequestCancellationStatus.REQUESTED);
        request.setCancellationReason(cancellationReason);
        request.setCancellationRequestedAt(LocalDateTime.now());
        DesignRequest saved = designRequestRepository.save(request);
        publishCancellationChanged(saved, currentUser);
        return toResponse(saved);
    }

    /**
     * Resolves a pending cancellation request. Approval cancels and unlocks the
     * booth; rejection resumes the existing main workflow status.
     */
    @Transactional
    public DesignRequestResponseDTO decideCancellation(UUID id, boolean approve, String note) {
        DesignRequest request = getRequest(id);
        if (request.getCancellationStatus() != DesignRequestCancellationStatus.REQUESTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        request.setCancellationResolutionNote(trimToNull(note));
        request.setCancellationResolvedAt(LocalDateTime.now());
        if (approve) {
            DesignRequestStatus previousStatus = request.getStatus();
            request.setCancellationStatus(DesignRequestCancellationStatus.APPROVED);
            request.setStatus(DesignRequestStatus.CANCELED);
            request.setCanceledAt(LocalDateTime.now());
            request.getBooth().setStatus(BoothStatus.DRAFT);
            DesignRequest saved = designRequestRepository.save(request);
            publishStatusChanged(saved, null, previousStatus);
            publishCancellationChanged(saved, null);
            promoteOldestQueued(request.getAssignedDesigner());
            return toResponse(saved);
        }
        request.setCancellationStatus(DesignRequestCancellationStatus.REJECTED);
        DesignRequest saved = designRequestRepository.save(request);
        publishCancellationChanged(saved, null);
        return toResponse(saved);
    }

    /**
     * Assigns a pending request to a user with the DESIGNER role. Assignment is
     * rejected when the Designer already has three active requests. The
     * Designer row is locked while workload is checked to serialize concurrent
     * assignments.
     *
     * @param id            design request identifier
     * @param assignRequest target Designer identifier
     * @return the assigned request
     * @throws AppException if the request is not PENDING, the target is not a
     *                      Designer, or the active-workload limit is reached
     */
    @Transactional
    public DesignRequestResponseDTO assignRequest(UUID id, AssignDesignRequest assignRequest) {
        DesignRequest request = getRequest(id);
        if (request.getStatus() != DesignRequestStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }

        User designer = userService.getUserEntityByIdForUpdate(assignRequest.getDesignerId());
        if (designer.getRole() != Role.DESIGNER) {
            throw new AppException(ErrorCode.INVALID_DESIGNER);
        }
        eligibilityService.assertCanAssign(request);
        long activeCount = designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                designer.getId(),
                DesignRequestRepository.WORKING_STATUSES);
        if (activeCount >= MAX_ACTIVE_REQUESTS_PER_DESIGNER) {
            throw new AppException(ErrorCode.DESIGNER_WORKLOAD_EXCEEDED);
        }

        DesignRequestStatus previousStatus = request.getStatus();
        request.setAssignedDesigner(designer);
        request.setAssignedAt(LocalDateTime.now());
        request.setStatus(DesignRequestStatus.ASSIGNED);
        request.getBooth().setStatus(BoothStatus.DESIGNING);
        baselineService.createWorkingBaseline(request);
        DesignRequest saved = designRequestRepository.save(request);
        publishStatusChanged(saved, null, previousStatus);
        return toResponse(saved);
    }

    /**
     * Creates and immediately submits an immutable draft in one operation. This
     * compatibility flow validates request-scoped panorama assets and
     * Exhibitor-owned product/media references, then moves the request to
     * DRAFT_SUBMITTED and removes abandoned staging assets.
     *
     * @param currentUser  authenticated assigned Designer
     * @param id           design request identifier
     * @param draftRequest complete panorama and hotspot configuration
     * @return the request after draft submission
     * @throws AppException if assignment, editable status, or draft validation
     *                      fails
     */
    @Transactional
    public DesignRequestResponseDTO submitDraft(User currentUser, UUID id, SubmitDesignDraftRequest draftRequest) {
        DesignRequest request = getRequest(id);
        requireDesignerCanEdit(currentUser, request);

        DesignRequestStatus previousStatus = request.getStatus();
        DesignDraft draft = buildDraft(request, draftRequest, nextSubmittedVersion(request));
        request.getDrafts().add(draft);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        request.setReviewNote(null);
        DesignRequest saved = designRequestRepository.save(request);
        designDraftAssetService.cleanupUnreferencedAssets(saved);
        publishStatusChanged(saved, currentUser, previousStatus);
        promoteOldestQueued(saved.getAssignedDesigner());
        return toResponse(saved);
    }

    /**
     * Creates or replaces the mutable working draft (version zero) without
     * changing the request status. Unreferenced staging assets are removed after
     * the replacement.
     *
     * @param currentUser  authenticated assigned Designer
     * @param id           design request identifier
     * @param draftRequest complete working panorama and hotspot configuration
     * @return the request with the updated working draft
     * @throws AppException if assignment, editable status, or draft validation
     *                      fails
     */
    @Transactional
    public DesignRequestResponseDTO saveWorkingDraft(
            User currentUser,
            UUID id,
            SubmitDesignDraftRequest draftRequest) {
        DesignRequest request = getRequest(id);
        requireDesignerCanEdit(currentUser, request);

        DesignDraft workingDraft = buildDraft(request, draftRequest, 0);
        request.getDrafts().removeIf(draft -> draft.getVersionNumber() == 0);
        request.getDrafts().add(workingDraft);
        DesignRequest saved = designRequestRepository.save(request);
        designDraftAssetService.cleanupUnreferencedAssets(saved);
        return toResponse(saved);
    }

    /**
     * Promotes the mutable working draft to the next immutable submitted
     * version and moves the request to DRAFT_SUBMITTED for Exhibitor review.
     *
     * @param currentUser authenticated assigned Designer
     * @param id          design request identifier
     * @return the request after submission
     * @throws AppException if the request is not editable, belongs to another
     *                      Designer, or has no working draft
     */
    @Transactional
    public DesignRequestResponseDTO submitWorkingDraft(User currentUser, UUID id) {
        DesignRequest request = getRequest(id);
        requireDesignerCanEdit(currentUser, request);
        DesignDraft workingDraft = request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() == 0)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));

        DesignRequestStatus previousStatus = request.getStatus();
        workingDraft.setVersionNumber(nextSubmittedVersion(request));
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        request.setReviewNote(null);
        DesignRequest saved = designRequestRepository.save(request);
        designDraftAssetService.cleanupUnreferencedAssets(saved);
        publishStatusChanged(saved, currentUser, previousStatus);
        promoteOldestQueued(saved.getAssignedDesigner());
        return toResponse(saved);
    }

    /**
     * Rejects the latest submitted draft and returns the request to the assigned
     * Designer for revision. Each rejection requires a note and increments both
     * the request review count and the booth's shared design-action usage. If
     * all Designer slots are occupied, the request enters REVISION_QUEUED.
     *
     * @param currentUser   authenticated Exhibitor that owns the request company
     * @param id            design request identifier
     * @param rejectRequest required revision note for the Designer
     * @return the request in REVISION_REQUESTED or REVISION_QUEUED status
     * @throws AppException if the request is not DRAFT_SUBMITTED, belongs to
     *                      another company, or the booth action quota is exhausted
     */
    @Transactional
    public DesignRequestResponseDTO rejectDraft(User currentUser, UUID id, RejectDesignDraftRequest rejectRequest) {
        Company company = getCompanyForCurrentUser(currentUser);
        DesignRequest request = getRequestForCompany(id, company);
        if (request.getStatus() != DesignRequestStatus.DRAFT_SUBMITTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        if (eligibilityService.remainingActions(request.getBooth()) == 0) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED);
        }

        DesignRequestStatus previousStatus = request.getStatus();
        request.setReviewCount(request.getReviewCount() + 1);
        String reviewNote = rejectRequest == null ? null : trimToNull(rejectRequest.getReviewNote());
        if (reviewNote == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        request.setReviewNote(reviewNote);
        User assignedDesigner = userService.getUserEntityByIdForUpdate(request.getAssignedDesigner().getId());
        long workingCount = designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                assignedDesigner.getId(), DesignRequestRepository.WORKING_STATUSES);
        if (workingCount >= MAX_ACTIVE_REQUESTS_PER_DESIGNER) {
            request.setStatus(DesignRequestStatus.REVISION_QUEUED);
            request.setRevisionQueuedAt(LocalDateTime.now());
        } else {
            request.setStatus(DesignRequestStatus.REVISION_REQUESTED);
            request.setRevisionQueuedAt(null);
        }
        DesignRequest saved = designRequestRepository.save(request);
        publishStatusChanged(saved, currentUser, previousStatus);
        return toResponse(saved);
    }

    /**
     * Approves the latest submitted draft and replaces the booth's panorama and
     * hotspot content with that draft. The request becomes APPROVED, the booth
     * returns to DRAFT for Exhibitor editing, and superseded design assets are
     * cleaned up.
     *
     * @param currentUser authenticated Exhibitor that owns the request company
     * @param id          design request identifier
     * @return the approved request
     * @throws AppException if the request is not DRAFT_SUBMITTED, belongs to
     *                      another company, or has no submitted draft
     */
    @Transactional
    public DesignRequestResponseDTO approveDraft(User currentUser, UUID id) {
        Company company = getCompanyForCurrentUser(currentUser);
        DesignRequest request = getRequestForCompany(id, company);
        if (request.getStatus() != DesignRequestStatus.DRAFT_SUBMITTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }

        DesignDraft draft = designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        DesignRequestStatus previousStatus = request.getStatus();
        applyDraftToBooth(request, draft);
        request.setStatus(DesignRequestStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        request.getBooth().setStatus(BoothStatus.DRAFT);
        DesignRequest saved = designRequestRepository.save(request);
        designDraftAssetService.cleanupAfterApproval(saved);
        publishStatusChanged(saved, currentUser, previousStatus);
        promoteOldestQueued(saved.getAssignedDesigner());
        return toResponse(saved);
    }

    /**
     * Forces cleanup of design assets no longer used by the current booth. The
     * caller must enforce the Admin authorization boundary; this operation only
     * accepts APPROVED or CANCELED requests.
     *
     * @param id terminal design request identifier
     * @return number of assets deleted
     * @throws AppException if the request is not APPROVED or CANCELED
     */
    @Transactional
    public int cleanupTerminalAssets(UUID id) {
        DesignRequest request = getRequest(id);
        if (request.getStatus() != DesignRequestStatus.APPROVED
                && request.getStatus() != DesignRequestStatus.CANCELED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        return designDraftAssetService.cleanupAfterApproval(request);
    }

    /**
     * Aggregates pending, working, waiting-review, queued, completed, canceled,
     * and per-Designer workload metrics with optional mode/scope filters.
     *
     * @return current design-assignment analytics
     */
    @Transactional(readOnly = true)
    public DesignAssignmentAnalyticsResponseDTO getAssignmentAnalytics(
            com.example.vex360.features.designrequest.enums.DesignRequestMode mode,
            com.example.vex360.features.designrequest.enums.DesignRequestScope scope) {
        List<DesignRequestStatus> activeStatuses = DesignRequestRepository.WORKING_STATUSES;
        List<DesignerWorkloadResponseDTO> workloads = userRepository
                .findByRoleAndStatusOrderByFullNameAsc(Role.DESIGNER, UserStatus.ACTIVE).stream()
                .map(designer -> toWorkload(designer, mode, scope))
                .toList();
        return new DesignAssignmentAnalyticsResponseDTO(
                designRequestRepository.countFiltered(DesignRequestStatus.PENDING, mode, scope),
                designRequestRepository.countFilteredIn(activeStatuses, mode, scope),
                designRequestRepository.countFiltered(DesignRequestStatus.DRAFT_SUBMITTED, mode, scope),
                designRequestRepository.countFiltered(DesignRequestStatus.REVISION_QUEUED, mode, scope),
                designRequestRepository.countFiltered(DesignRequestStatus.APPROVED, mode, scope),
                designRequestRepository.countFiltered(DesignRequestStatus.CANCELED, mode, scope),
                workloads);
    }

    /**
     * Lists active Designers including those with zero workload, ordered by
     * available slots and then display name.
     */
    @Transactional(readOnly = true)
    public List<DesignAssignmentCandidateResponseDTO> getAssignmentCandidates() {
        return userRepository.findByRoleAndStatusOrderByFullNameAsc(Role.DESIGNER, UserStatus.ACTIVE).stream()
                .map(designer -> {
                    long working = designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                            designer.getId(), DesignRequestRepository.WORKING_STATUSES);
                    return new DesignAssignmentCandidateResponseDTO(
                            designer.getId(), designer.getFullName(), designer.getEmail(), working,
                            Math.max(0, MAX_ACTIVE_REQUESTS_PER_DESIGNER - (int) working));
                })
                .sorted(java.util.Comparator
                        .comparingInt(DesignAssignmentCandidateResponseDTO::getAvailableSlots).reversed()
                        .thenComparing(candidate -> candidate.getDesignerName() == null
                                ? "" : candidate.getDesignerName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private DesignerWorkloadResponseDTO toWorkload(
            User designer,
            com.example.vex360.features.designrequest.enums.DesignRequestMode mode,
            com.example.vex360.features.designrequest.enums.DesignRequestScope scope) {
        long working = designRequestRepository.countDesignerFilteredIn(
                designer.getId(), DesignRequestRepository.WORKING_STATUSES, mode, scope);
        long waiting = designRequestRepository.countDesignerFilteredIn(
                designer.getId(), List.of(DesignRequestStatus.DRAFT_SUBMITTED), mode, scope);
        long queued = designRequestRepository.countDesignerFilteredIn(
                designer.getId(), List.of(DesignRequestStatus.REVISION_QUEUED), mode, scope);
        return new DesignerWorkloadResponseDTO(
                designer.getId(), designer.getFullName(), designer.getEmail(),
                working, waiting, queued, Math.max(0, MAX_ACTIVE_REQUESTS_PER_DESIGNER - (int) working));
    }

    private DesignDraft buildDraft(
            DesignRequest request,
            SubmitDesignDraftRequest draftRequest,
            int versionNumber) {
        if (draftRequest == null || draftRequest.getPanoramas() == null || draftRequest.getPanoramas().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }

        DesignDraft draft = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(versionNumber)
                .note(trimToNull(draftRequest.getNote()))
                .build();
        draftSettingsService.applyToDraft(request, draft, draftRequest.getBoothSettings());

        Set<String> keys = new HashSet<>();
        long defaultCount = draftRequest.getPanoramas().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDefault()))
                .count();
        if (defaultCount > 1) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }

        List<DesignDraftPanorama> panoramas = new ArrayList<>();
        for (SubmitDesignDraftPanoramaRequest panoramaRequest : draftRequest.getPanoramas()) {
            String key = trimToNull(panoramaRequest.getClientKey());
            if (key == null || !keys.add(key)) {
                throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
            }
            String imageUrl = requireText(panoramaRequest.getImageUrl());
            String imageKey = requireText(panoramaRequest.getImageKey());
            designDraftAssetService.requireDraftAsset(request, imageKey, imageUrl);
            DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                    .draft(draft)
                    .clientKey(key)
                    .name(requireText(panoramaRequest.getName()))
                    .imageUrl(imageUrl)
                    .imageKey(imageKey)
                    .orderIndex(panoramaRequest.getOrderIndex())
                    .isDefault(Boolean.TRUE.equals(panoramaRequest.getIsDefault()))
                    .build();
            panoramas.add(panorama);
        }
        if (defaultCount == 0) {
            panoramas.get(0).setIsDefault(true);
        }

        Map<String, DesignDraftPanorama> panoramasByKey = panoramas.stream()
                .collect(Collectors.toMap(DesignDraftPanorama::getClientKey, Function.identity()));
        for (int i = 0; i < draftRequest.getPanoramas().size(); i++) {
            SubmitDesignDraftPanoramaRequest panoramaRequest = draftRequest.getPanoramas().get(i);
            DesignDraftPanorama panorama = panoramas.get(i);
            List<SubmitDesignDraftHotspotRequest> hotspotRequests = panoramaRequest.getHotspots() == null
                    ? List.of()
                    : panoramaRequest.getHotspots();
            for (SubmitDesignDraftHotspotRequest hotspotRequest : hotspotRequests) {
                DesignDraftHotspot hotspot = buildDraftHotspot(
                        request,
                        panorama,
                        panoramasByKey,
                        hotspotRequest);
                panorama.getHotspots().add(hotspot);
            }
        }

        draft.getPanoramas().addAll(panoramas);
        return draft;
    }

    private int nextSubmittedVersion(DesignRequest request) {
        return request.getDrafts().stream()
                .map(DesignDraft::getVersionNumber)
                .filter(version -> version > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void requireDesignerCanEdit(User currentUser, DesignRequest request) {
        User designer = requireCurrentUser(currentUser);
        if (request.getAssignedDesigner() == null
                || !request.getAssignedDesigner().getId().equals(designer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (request.getStatus() != DesignRequestStatus.ASSIGNED
                && request.getStatus() != DesignRequestStatus.REVISION_REQUESTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        if (request.getCancellationStatus() == DesignRequestCancellationStatus.REQUESTED) {
            throw new AppException(ErrorCode.DESIGN_CANCELLATION_PENDING);
        }
    }

    private DesignDraftHotspot buildDraftHotspot(
            DesignRequest designRequest,
            DesignDraftPanorama sourcePanorama,
            Map<String, DesignDraftPanorama> panoramasByKey,
            SubmitDesignDraftHotspotRequest request) {
        if (request == null || request.getType() == null
                || request.getXPosition() == null
                || request.getYPosition() == null
                || request.getZPosition() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }

        DesignDraftHotspot hotspot = DesignDraftHotspot.builder()
                .sourcePanorama(sourcePanorama)
                .type(request.getType())
                .xPosition(request.getXPosition())
                .yPosition(request.getYPosition())
                .zPosition(request.getZPosition())
                .iconStyle(trimToNull(request.getIconStyle()))
                .scale(request.getScale())
                .zIndex(request.getZIndex())
                .build();
        applyCorners(hotspot, request.getType(), request.getCorners());

        if (designRequest.getScope() == DesignRequestScope.SPATIAL
                && request.getType() != HotspotType.NAV) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }

        switch (request.getType()) {
            case NAV -> applyDraftNavHotspot(hotspot, panoramasByKey, request);
            case PRODUCT -> applyDraftProductHotspot(hotspot, designRequest, request);
            case INFO -> applyDraftInfoHotspot(hotspot, designRequest, request);
            case MEDIA -> applyDraftMediaHotspot(hotspot, designRequest.getCompany(), request);
            default -> throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return hotspot;
    }

    private void applyDraftNavHotspot(
            DesignDraftHotspot hotspot,
            Map<String, DesignDraftPanorama> panoramasByKey,
            SubmitDesignDraftHotspotRequest request) {
        String targetKey = trimToNull(request.getTargetDraftPanoramaKey());
        if (targetKey == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignDraftPanorama target = panoramasByKey.get(targetKey);
        if (target == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        hotspot.setTargetDraftPanoramaKey(targetKey);
        hotspot.setName(resolveName(request.getName(), target.getName()));
    }

    private void applyDraftProductHotspot(
            DesignDraftHotspot hotspot,
            DesignRequest designRequest,
            SubmitDesignDraftHotspotRequest request) {
        if (request.getProductId() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        requestProductService.assertProductAllowed(designRequest, request.getProductId());
        Product product = productService.getProductForCompany(request.getProductId(), designRequest.getCompany());
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(request.getName(), product.getName()));
    }

    private void applyDraftInfoHotspot(
            DesignDraftHotspot hotspot,
            DesignRequest designRequest,
            SubmitDesignDraftHotspotRequest request) {
        HotspotInfoContentType contentType = resolveInfoContentType(request);
        hotspot.setInfoContentType(contentType);
        hotspot.setName(resolveName(request.getName(), "Info"));
        switch (contentType) {
            case NONE -> {
                hotspot.setInfoText(trimToNull(request.getInfoText()));
            }
            case PRODUCT -> applyDraftProductHotspot(hotspot, designRequest, request);
            case IMAGE ->
                hotspot.setMediaAsset(getMediaAsset(
                        request.getMediaAssetId(), designRequest.getCompany(), MediaAssetType.IMAGE));
            case VIDEO ->
                hotspot.setMediaAsset(getMediaAsset(
                        request.getMediaAssetId(), designRequest.getCompany(), MediaAssetType.VIDEO));
            default -> throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
    }

    private void applyDraftMediaHotspot(
            DesignDraftHotspot hotspot,
            Company company,
            SubmitDesignDraftHotspotRequest request) {
        hotspot.setMediaAsset(getMediaAsset(request.getMediaAssetId(), company, null));
        hotspot.setMediaClickAction(request.getMediaClickAction() == null
                ? HotspotMediaClickAction.DEFAULT
                : request.getMediaClickAction());
        hotspot.setName(resolveName(request.getName(), hotspot.getMediaAsset().getName()));
    }

    private void applyDraftToBooth(DesignRequest request, DesignDraft draft) {
        boothDesignService.replaceBoothContent(
                request.getBooth(),
                draft.getPanoramas().stream()
                        .map(this::toPanoramaDesign)
                        .toList());
        if (request.getScope() == DesignRequestScope.FULL) {
            draftSettingsService.applyToBooth(request.getBooth(), draft);
        }
    }

    private PanoramaDesign toPanoramaDesign(DesignDraftPanorama draftPanorama) {
        return new PanoramaDesign(
                draftPanorama.getClientKey(),
                draftPanorama.getName(),
                draftPanorama.getImageUrl(),
                draftPanorama.getImageKey(),
                draftPanorama.getOrderIndex(),
                draftPanorama.getIsDefault(),
                draftPanorama.getHotspots().stream().map(this::toHotspotDesign).toList());
    }

    private HotspotDesign toHotspotDesign(DesignDraftHotspot draftHotspot) {
        return new HotspotDesign(
                draftHotspot.getType(),
                draftHotspot.getName(),
                draftHotspot.getTargetDraftPanoramaKey(),
                draftHotspot.getProduct(),
                draftHotspot.getMediaAsset(),
                draftHotspot.getInfoText(),
                draftHotspot.getXPosition(),
                draftHotspot.getYPosition(),
                draftHotspot.getZPosition(),
                draftHotspot.getIconStyle(),
                draftHotspot.getScale(),
                draftHotspot.getZIndex(),
                draftHotspot.getMediaClickAction(),
                draftHotspot.getInfoContentType(),
                draftHotspot.getCornerTlX(),
                draftHotspot.getCornerTlY(),
                draftHotspot.getCornerTlZ(),
                draftHotspot.getCornerTrX(),
                draftHotspot.getCornerTrY(),
                draftHotspot.getCornerTrZ(),
                draftHotspot.getCornerBlX(),
                draftHotspot.getCornerBlY(),
                draftHotspot.getCornerBlZ(),
                draftHotspot.getCornerBrX(),
                draftHotspot.getCornerBrY(),
                draftHotspot.getCornerBrZ());
    }

    private MediaAsset getMediaAsset(UUID mediaAssetId, Company company, MediaAssetType expectedType) {
        if (mediaAssetId == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return boothDesignService.getMediaAssetForCompany(mediaAssetId, company.getId(), expectedType);
    }

    private HotspotInfoContentType resolveInfoContentType(SubmitDesignDraftHotspotRequest request) {
        if (request.getInfoContentType() != null) {
            return request.getInfoContentType();
        }
        if (request.getProductId() != null) {
            return HotspotInfoContentType.PRODUCT;
        }
        if (request.getMediaAssetId() != null) {
            return HotspotInfoContentType.IMAGE;
        }
        return HotspotInfoContentType.NONE;
    }

    private void applyCorners(DesignDraftHotspot hotspot, HotspotType type, HotspotCornersDTO corners) {
        if (type != HotspotType.MEDIA && type != HotspotType.PRODUCT) {
            return;
        }
        if (corners == null) {
            return;
        }
        if (!isCorner(corners.getTl()) || !isCorner(corners.getTr())
                || !isCorner(corners.getBl()) || !isCorner(corners.getBr())) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        hotspot.setCornerTlX(corners.getTl().get(0));
        hotspot.setCornerTlY(corners.getTl().get(1));
        hotspot.setCornerTlZ(corners.getTl().get(2));
        hotspot.setCornerTrX(corners.getTr().get(0));
        hotspot.setCornerTrY(corners.getTr().get(1));
        hotspot.setCornerTrZ(corners.getTr().get(2));
        hotspot.setCornerBlX(corners.getBl().get(0));
        hotspot.setCornerBlY(corners.getBl().get(1));
        hotspot.setCornerBlZ(corners.getBl().get(2));
        hotspot.setCornerBrX(corners.getBr().get(0));
        hotspot.setCornerBrY(corners.getBr().get(1));
        hotspot.setCornerBrZ(corners.getBr().get(2));
    }

    private boolean isCorner(List<Double> corner) {
        return corner != null && corner.size() == 3
                && corner.get(0) != null && corner.get(1) != null && corner.get(2) != null;
    }

    private DesignRequest getRequestForCompany(UUID id, Company company) {
        DesignRequest request = getRequest(id);
        if (!request.getCompany().getId().equals(company.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return request;
    }

    private DesignRequest getRequest(UUID id) {
        return designRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
    }

    private Booth getCompanyBoothForUpdate(UUID boothId, Company company) {
        return boothDesignService.getCompanyBoothForUpdate(boothId, company.getId());
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        User user = requireCurrentUser(currentUser);
        return companyService.getCompanyEntityForCurrentUser(user);
    }

    private User requireCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return currentUser;
    }

    private void publishStatusChanged(
            DesignRequest request,
            User actor,
            DesignRequestStatus previousStatus) {
        User assignedDesigner = request.getAssignedDesigner();
        eventPublisher.publishEvent(new DesignRequestStatusChangedEvent(
                request.getId(),
                request.getCompany().getId(),
                assignedDesigner == null ? null : assignedDesigner.getId(),
                actor == null ? null : actor.getId(),
                previousStatus,
                request.getStatus()));
    }

    private void publishCancellationChanged(DesignRequest request, User actor) {
        eventPublisher.publishEvent(new DesignRequestCancellationChangedEvent(
                request.getId(),
                request.getCompany().getId(),
                request.getAssignedDesigner() == null ? null : request.getAssignedDesigner().getId(),
                actor == null ? null : actor.getId(),
                request.getCancellationStatus()));
    }

    private void promoteOldestQueued(User designer) {
        if (designer == null) {
            return;
        }
        userService.getUserEntityByIdForUpdate(designer.getId());
        long workingCount = designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                designer.getId(), DesignRequestRepository.WORKING_STATUSES);
        while (workingCount < MAX_ACTIVE_REQUESTS_PER_DESIGNER) {
            DesignRequest queued = designRequestRepository
                    .findFirstByAssignedDesignerIdAndStatusOrderByRevisionQueuedAtAsc(
                            designer.getId(), DesignRequestStatus.REVISION_QUEUED)
                    .orElse(null);
            if (queued == null) {
                return;
            }
            DesignRequestStatus previousStatus = queued.getStatus();
            queued.setStatus(DesignRequestStatus.REVISION_REQUESTED);
            queued.setRevisionQueuedAt(null);
            designRequestRepository.save(queued);
            publishStatusChanged(queued, null, previousStatus);
            eventPublisher.publishEvent(new DesignRequestRevisionPromotedEvent(queued.getId(), designer.getId()));
            workingCount++;
        }
    }

    private String requireText(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        return trimmed;
    }

    private String resolveName(String requestedName, String fallbackName) {
        String name = trimToNull(requestedName);
        if (name != null) {
            return name;
        }
        return requireText(fallbackName);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DesignRequestResponseDTO toResponse(DesignRequest request) {
        DesignRequestResponseDTO response = designRequestMapper.toResponse(request);
        response.setRemainingDesignActions(eligibilityService.remainingActions(request.getBooth()));
        return response;
    }
}
