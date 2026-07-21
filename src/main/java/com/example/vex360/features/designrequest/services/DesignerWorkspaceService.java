package com.example.vex360.features.designrequest.services;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

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
    private final ProductService productService;
    private final BoothDesignService boothDesignService;

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
        return new DesignerWorkspaceResponseDTO(
                request.getId(),
                request.getStatus(),
                request.getNote(),
                request.getReviewNote(),
                request.getReviewCount(),
                boothMapper.toBoothResponseDTO(request.getBooth()),
                toDraftResponse(working),
                toDraftResponse(latestSubmitted));
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
        DesignRequest request = getAssignedRequest(designer, requestId);
        return productService.getActiveProductsForCompany(
                request.getCompany().getId(),
                keyword,
                categoryId,
                pageable);
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
        if (request.getAssignedDesigner() == null
                || !designer.getId().equals(request.getAssignedDesigner().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return request;
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
                new SubmitDesignDraftRequest(draft.getNote(), panoramas));
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
