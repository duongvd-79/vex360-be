package com.example.vex360.features.designrequest.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.example.vex360.features.designrequest.dtos.response.DesignDraftResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkloadResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.events.DesignRequestStatusChangedEvent;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
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
    private static final int MAX_BOOTH_DESIGN_ACTIONS = 3;
    private static final int MAX_ACTIVE_REQUESTS_PER_DESIGNER = 3;

    private final DesignRequestRepository designRequestRepository;
    private final DesignDraftRepository designDraftRepository;
    private final BoothDesignService boothDesignService;
    private final CompanyService companyService;
    private final UserService userService;
    private final ProductService productService;
    private final DesignDraftAssetService designDraftAssetService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a pending design request for an Exhibitor-owned booth. The booth
     * must be in DRAFT and have remaining design-action quota. On success the
     * booth moves to DESIGNING so Exhibitor editing is blocked.
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
        if (booth.getStatus() != BoothStatus.DRAFT) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        assertBoothDesignQuotaAvailable(booth);
        booth.setStatus(BoothStatus.DESIGNING);

        DesignRequest designRequest = DesignRequest.builder()
                .booth(booth)
                .company(company)
                .requestedBy(currentUser)
                .note(trimToNull(request.getNote()))
                .status(DesignRequestStatus.PENDING)
                .reviewCount(0)
                .build();
        DesignRequest saved = designRequestRepository.save(designRequest);
        publishStatusChanged(saved, currentUser, null);
        return toResponse(saved);
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
            Pageable pageable) {
        return PageResponse.from(designRequestRepository.searchForAdmin(status, designerId, pageable)
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
     * is unlocked by returning it from DESIGNING to DRAFT.
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
        request.setCanceledAt(LocalDateTime.now());
        request.getBooth().setStatus(BoothStatus.DRAFT);
        DesignRequest saved = designRequestRepository.save(request);
        publishStatusChanged(saved, currentUser, previousStatus);
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
        long activeCount = designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                designer.getId(),
                DesignRequestRepository.ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_REQUESTS_PER_DESIGNER) {
            throw new AppException(ErrorCode.DESIGNER_WORKLOAD_EXCEEDED);
        }

        DesignRequestStatus previousStatus = request.getStatus();
        request.setAssignedDesigner(designer);
        request.setAssignedAt(LocalDateTime.now());
        request.setStatus(DesignRequestStatus.ASSIGNED);
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
        return toResponse(saved);
    }

    /**
     * Rejects the latest submitted draft and returns the request to the assigned
     * Designer for revision. Each rejection increments both the request review
     * count and the booth's shared design-action usage.
     *
     * @param currentUser   authenticated Exhibitor that owns the request company
     * @param id            design request identifier
     * @param rejectRequest optional revision note for the Designer
     * @return the request in REVISION_REQUESTED status
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
        assertBoothDesignQuotaAvailable(request.getBooth());

        DesignRequestStatus previousStatus = request.getStatus();
        request.setReviewCount(request.getReviewCount() + 1);
        request.setReviewNote(rejectRequest == null ? null : trimToNull(rejectRequest.getReviewNote()));
        request.setStatus(DesignRequestStatus.REVISION_REQUESTED);
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
     * Aggregates assignment metrics: pending requests, active requests,
     * completed/canceled totals, and active workload per Designer.
     *
     * @return current design-assignment analytics
     */
    @Transactional(readOnly = true)
    public DesignAssignmentAnalyticsResponseDTO getAssignmentAnalytics() {
        List<DesignRequestStatus> activeStatuses = DesignRequestRepository.ACTIVE_STATUSES;
        List<DesignerWorkloadResponseDTO> workloads = designRequestRepository.countActiveRequestsByDesigner(
                activeStatuses).stream()
                .map(row -> new DesignerWorkloadResponseDTO(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Long) row[3]))
                .toList();
        return new DesignAssignmentAnalyticsResponseDTO(
                designRequestRepository.countByStatus(DesignRequestStatus.PENDING),
                designRequestRepository.countByStatusIn(activeStatuses),
                designRequestRepository.countByStatus(DesignRequestStatus.APPROVED),
                designRequestRepository.countByStatus(DesignRequestStatus.CANCELED),
                workloads);
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
                        request.getCompany(),
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
    }

    private DesignDraftHotspot buildDraftHotspot(
            Company company,
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

        switch (request.getType()) {
            case NAV -> applyDraftNavHotspot(hotspot, panoramasByKey, request);
            case PRODUCT -> applyDraftProductHotspot(hotspot, company, request);
            case INFO -> applyDraftInfoHotspot(hotspot, company, request);
            case MEDIA -> applyDraftMediaHotspot(hotspot, company, request);
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
            Company company,
            SubmitDesignDraftHotspotRequest request) {
        if (request.getProductId() == null) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        Product product = productService.getProductForCompany(request.getProductId(), company);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(request.getName(), product.getName()));
    }

    private void applyDraftInfoHotspot(
            DesignDraftHotspot hotspot,
            Company company,
            SubmitDesignDraftHotspotRequest request) {
        HotspotInfoContentType contentType = resolveInfoContentType(request);
        hotspot.setInfoContentType(contentType);
        hotspot.setName(resolveName(request.getName(), "Info"));
        switch (contentType) {
            case NONE -> {
                hotspot.setInfoText(trimToNull(request.getInfoText()));
            }
            case PRODUCT -> applyDraftProductHotspot(hotspot, company, request);
            case IMAGE ->
                hotspot.setMediaAsset(getMediaAsset(request.getMediaAssetId(), company, MediaAssetType.IMAGE));
            case VIDEO ->
                hotspot.setMediaAsset(getMediaAsset(request.getMediaAssetId(), company, MediaAssetType.VIDEO));
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

    private void assertBoothDesignQuotaAvailable(Booth booth) {
        long actions = designRequestRepository.countByBoothId(booth.getId())
                + designRequestRepository.sumReviewCountByBoothId(booth.getId());
        if (actions >= MAX_BOOTH_DESIGN_ACTIONS) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED);
        }
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

    private DesignRequestResponseDTO toResponse(DesignRequest request) {
        Booth booth = request.getBooth();
        Company company = request.getCompany();
        User designer = request.getAssignedDesigner();
        return new DesignRequestResponseDTO(
                request.getId(),
                booth == null ? null : booth.getId(),
                booth == null ? null : booth.getName(),
                company == null ? null : company.getId(),
                request.getStatus(),
                designer == null ? null : designer.getId(),
                designer == null ? null : designer.getFullName(),
                request.getNote(),
                request.getReviewNote(),
                request.getReviewCount(),
                toDraftResponse(latestDraft(request)),
                request.getCreatedAt(),
                request.getAssignedAt(),
                request.getApprovedAt(),
                request.getCanceledAt());
    }

    private DesignDraft latestDraft(DesignRequest request) {
        if (request.getDrafts() == null || request.getDrafts().isEmpty()) {
            return null;
        }
        return request.getDrafts().stream()
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .orElse(null);
    }

    private DesignDraftResponseDTO toDraftResponse(DesignDraft draft) {
        if (draft == null) {
            return null;
        }
        return new DesignDraftResponseDTO(
                draft.getId(),
                draft.getVersionNumber(),
                draft.getNote(),
                draft.getCreatedAt());
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
}
