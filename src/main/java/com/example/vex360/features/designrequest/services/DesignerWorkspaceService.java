package com.example.vex360.features.designrequest.services;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.request.DesignDraftBoothSettingsRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.ExhibitorDesignReviewWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;

import lombok.RequiredArgsConstructor;

/**
 * Provides designer-scoped access to an assigned design request workspace and
 * the Exhibitor-owned resources that may be referenced by a booth draft.
 * Every public query validates the request assignment before exposing booth or
 * company data.
 */
@Service
@RequiredArgsConstructor
public class DesignerWorkspaceService {
    private static final int WORKING_VERSION = 0;

    private final DesignRequestRepository designRequestRepository;
    private final BoothMapper boothMapper;
    private final DesignRequestProductRepository requestProductRepository;
    private final ProductMapper productMapper;
    private final BoothDesignService boothDesignService;
    private final CompanyService companyService;
    private final DesignRequestEligibilityService eligibilityService;
    private final DesignDraftBenefitGuardService benefitGuardService;

    /**
     * Builds the workspace for an assigned request, including the current booth,
     * mutable working draft, and latest submitted draft.
     *
     * @param designer  authenticated Designer
     * @param requestId design request identifier
     * @return the request workspace visible to the assigned Designer
     * @throws AppException if the user is unauthenticated, the request does not
     *                      exist, or the request is assigned to another Designer
     */
    @Transactional(readOnly = true)
    public DesignerWorkspaceResponseDTO getWorkspace(User designer, UUID requestId) {
        DesignRequest request = getAssignedRequest(designer, requestId);
        DesignDraft working = request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() == WORKING_VERSION)
                .findFirst()
                .orElse(null);
        DesignDraft latestSubmitted = request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() > WORKING_VERSION)
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .orElse(null);
        DesignDraft usageDraft = working == null ? latestSubmitted : working;
        return new DesignerWorkspaceResponseDTO(
                request.getId(),
                request.getStatus(),
                request.getMode(),
                request.getCancellationStatus(),
                eligibilityService.remainingActions(request.getBooth()),
                (int) request.getProducts().stream()
                        .filter(item -> Boolean.TRUE.equals(item.getRequiredFromBaseline())).count(),
                (int) request.getProducts().stream()
                        .filter(item -> !Boolean.TRUE.equals(item.getRequiredFromBaseline())).count(),
                request.getNote(),
                request.getReviewNote(),
                request.getReviewCount(),
                boothMapper.toBoothResponseDTO(request.getBooth()),
                toDraftResponse(working),
                toDraftResponse(latestSubmitted),
                isEditable(request, working),
                usageDraft == null
                        ? benefitGuardService.getBaselineUsageResponse(request)
                        : benefitGuardService.getUsageResponse(request, usageDraft));
    }

    /**
     * Returns active products owned by the request's Exhibitor company. These
     * products are reference data for PRODUCT or INFO hotspots and are not
     * modified by this operation.
     *
     * @param designer   authenticated Designer
     * @param requestId  design request identifier
     * @param keyword    optional product search term
     * @param categoryId optional product category filter
     * @param pageable   pagination and sorting options
     * @return a page of active products from the request company
     * @throws AppException if the Designer is not assigned to the request
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getProducts(
            User designer,
            UUID requestId,
            String keyword,
            UUID categoryId,
            Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return PageResponse.from(requestProductRepository
                .searchAllowedProducts(requestId, ProductStatus.ACTIVE, normalizedKeyword, categoryId, pageable)
                .map(item -> productMapper.toResponse(item.getProduct())));
    }

    /**
     * Returns media assets owned by the request's Exhibitor company for use in
     * MEDIA or INFO hotspots.
     *
     * @param designer  authenticated Designer
     * @param requestId design request identifier
     * @param pageable  pagination and sorting options
     * @return a page of company media assets
     * @throws AppException if the Designer is not assigned to the request
     */
    @Transactional(readOnly = true)
    public PageResponse<MediaAssetResponseDTO> getMediaAssets(User designer, UUID requestId, Pageable pageable) {
        DesignRequest request = getAssignedRequest(designer, requestId);
        return PageResponse.from(boothDesignService.getMediaAssetsForCompany(request.getCompany().getId(), pageable)
                .map(boothMapper::toMediaAssetResponseDTO));
    }

    /**
     * Loads a request and enforces that it belongs to the authenticated
     * Designer. This method is also the authorization boundary reused by other
     * Designer services.
     *
     * @param designer  authenticated Designer
     * @param requestId design request identifier
     * @return the assigned design request entity
     * @throws AppException if authentication, lookup, or assignment validation
     *                      fails
     */
    public DesignRequest getAssignedRequest(User designer, UUID requestId) {
        if (designer == null || designer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        DesignRequest request = designRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
        requireAssignedDesigner(designer, request);
        return request;
    }

    /** Loads and locks an assigned request for Designer mutations. */
    @Transactional
    public DesignRequest getAssignedRequestForUpdate(User designer, UUID requestId) {
        if (designer == null || designer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        DesignRequest request = designRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
        requireAssignedDesigner(designer, request);
        return request;
    }

    private void requireAssignedDesigner(User designer, DesignRequest request) {
        if (request.getAssignedDesigner() == null
                || !designer.getId().equals(request.getAssignedDesigner().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private DesignDraftWorkspaceResponseDTO toDraftResponse(DesignDraft draft) {
        if (draft == null) {
            return null;
        }
        List<SubmitDesignDraftPanoramaRequest> panoramas = draft.getPanoramas().stream()
                .sorted(Comparator.comparing(DesignDraftPanorama::getOrderIndex))
                .map(this::toPanoramaRequest)
                .toList();
        return new DesignDraftWorkspaceResponseDTO(
                draft.getId(),
                draft.getVersionNumber(),
                draft.getCreatedAt(),
                new SubmitDesignDraftRequest(draft.getNote(), toSettings(draft), panoramas));
    }

    private boolean isEditable(DesignRequest request, DesignDraft working) {
        return working != null
                && working.getVersionNumber() == WORKING_VERSION
                && (request.getStatus() == DesignRequestStatus.ASSIGNED
                        || request.getStatus() == DesignRequestStatus.REVISION_REQUESTED)
                && request.getCancellationStatus() != DesignRequestCancellationStatus.REQUESTED;
    }

    /**
     * Builds the workspace for the Exhibitor to review the latest submitted design
     * draft.
     * Enforces company ownership and that the request is currently submitted for
     * review.
     *
     * @param exhibitor authenticated Exhibitor
     * @param requestId design request identifier
     * @return the review workspace details response DTO
     * @throws AppException if unauthorized, request not found, or status is not
     *                      DRAFT_SUBMITTED
     */
    @Transactional(readOnly = true)
    public ExhibitorDesignReviewWorkspaceResponseDTO getReviewWorkspace(User exhibitor, UUID requestId) {
        Company company = companyService.getCompanyEntityForCurrentUser(exhibitor);
        DesignRequest request = designRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
        if (!request.getCompany().getId().equals(company.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (request.getStatus() != DesignRequestStatus.DRAFT_SUBMITTED) {
            throw new AppException(ErrorCode.INVALID_DESIGN_REQUEST_STATUS);
        }
        DesignDraft latest = request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() > WORKING_VERSION)
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_DESIGN_DRAFT));
        List<ProductResponseDTO> required = request.getProducts().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequiredFromBaseline()))
                .map(item -> productMapper.toResponse(item.getProduct()))
                .toList();
        List<ProductResponseDTO> optional = request.getProducts().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getRequiredFromBaseline()))
                .map(item -> productMapper.toResponse(item.getProduct()))
                .toList();
        Map<UUID, MediaAsset> media = new LinkedHashMap<>();
        latest.getPanoramas().stream()
                .flatMap(panorama -> panorama.getHotspots().stream())
                .map(DesignDraftHotspot::getMediaAsset)
                .filter(Objects::nonNull)
                .forEach(asset -> media.putIfAbsent(asset.getId(), asset));
        return new ExhibitorDesignReviewWorkspaceResponseDTO(
                request.getId(),
                request.getStatus(),
                request.getMode(),
                eligibilityService.remainingActions(request.getBooth()),
                boothMapper.toBoothResponseDTO(request.getBooth()),
                toDraftResponse(latest),
                required,
                optional,
                media.values().stream().map(boothMapper::toMediaAssetResponseDTO).toList());
    }

    private DesignDraftBoothSettingsRequest toSettings(DesignDraft draft) {
        return new DesignDraftBoothSettingsRequest(
                draft.getBoothName(),
                draft.getBoothDescription(),
                draft.getDisplayTemplateKey(),
                draft.getThumbnailAction(),
                draft.getThumbnailAsset() == null ? null : draft.getThumbnailAsset().getId(),
                draft.getBackgroundMusicAction(),
                draft.getBackgroundMusicAsset() == null ? null : draft.getBackgroundMusicAsset().getId());
    }

    private SubmitDesignDraftPanoramaRequest toPanoramaRequest(DesignDraftPanorama panorama) {
        return new SubmitDesignDraftPanoramaRequest(
                panorama.getClientKey(),
                panorama.getName(),
                panorama.getImageUrl(),
                panorama.getImageKey(),
                panorama.getOrderIndex(),
                panorama.getIsDefault(),
                panorama.getHotspots().stream().map(this::toHotspotRequest).toList());
    }

    private SubmitDesignDraftHotspotRequest toHotspotRequest(DesignDraftHotspot hotspot) {
        return new SubmitDesignDraftHotspotRequest(
                hotspot.getType(),
                hotspot.getName(),
                hotspot.getXPosition(),
                hotspot.getYPosition(),
                hotspot.getZPosition(),
                hotspot.getTargetDraftPanoramaKey(),
                hotspot.getProduct() == null ? null : hotspot.getProduct().getId(),
                hotspot.getMediaAsset() == null ? null : hotspot.getMediaAsset().getId(),
                hotspot.getInfoText(),
                hotspot.getIconStyle(),
                hotspot.getScale(),
                hotspot.getZIndex(),
                hotspot.getMediaClickAction(),
                hotspot.getInfoContentType(),
                hotspot.getCorners());
    }
}
