package com.example.vex360.features.booth.services.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.ProductPlacementProjection;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.product.dtos.response.ProductPlacementDTO;
import com.example.vex360.features.product.dtos.response.VisitorProductSearchResponseDTO;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VisitorBoothServiceImpl implements VisitorBoothService {

    private final ExhibitionService exhibitionService;
    private final BoothRepository boothRepository;
    private final HotspotRepository hotspotRepository;
    private final BoothMapper boothMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getPublishedBooths(
            UUID exhibitionUuid,
            String keyword,
            BoothListingPriority listingPriority,
            Pageable pageable) {
        ExhibitionResponseDTO exhibition = getActiveExhibition(exhibitionUuid);

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<BoothResponseDTO> booths = boothRepository.findPublishedBoothsByExhibitionUuid(
                exhibition.getUuid(),
                BoothStatus.PUBLISHED,
                normalizedKeyword,
                listingPriority,
                pageable
        ).map(boothMapper::toBoothResponseDTO);

        return PageResponse.from(booths);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisitorProductSearchResponseDTO> searchDisplayedProducts(
            UUID exhibitionUuid,
            String keyword,
            Pageable pageable) {
        ExhibitionResponseDTO exhibition = getActiveExhibition(exhibitionUuid);
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<Product> productPage = hotspotRepository.searchDisplayedProductsForVisitor(
                exhibition.getUuid(),
                normalizedKeyword,
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED,
                pageable);

        if (productPage.isEmpty()) {
            return PageResponse.from(productPage.map(product -> toVisitorProductResponse(product, List.of())));
        }

        List<UUID> productIds = productPage.getContent().stream()
                .map(Product::getId)
                .toList();
        List<ProductPlacementProjection> placementRows = hotspotRepository.findProductPlacements(
                exhibition.getUuid(),
                productIds,
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED);

        Map<UUID, LinkedHashMap<UUID, ProductPlacementDTO>> placementsByProduct = new LinkedHashMap<>();
        for (ProductPlacementProjection row : placementRows) {
            placementsByProduct
                    .computeIfAbsent(row.getProductId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(row.getHotspotId(), toProductPlacement(row));
        }

        Page<VisitorProductSearchResponseDTO> responsePage = productPage.map(product -> {
            Map<UUID, ProductPlacementDTO> placementMap = placementsByProduct.get(product.getId());
            List<ProductPlacementDTO> placements = placementMap == null
                    ? List.of()
                    : new ArrayList<>(placementMap.values());
            return toVisitorProductResponse(product, placements);
        });
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public BoothResponseDTO getBoothTourDetail(UUID exhibitionUuid, UUID boothId) {
        ExhibitionResponseDTO exhibition = getActiveExhibition(exhibitionUuid);

        BoothResponseDTO boothTour = boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibition.getUuid(),
                boothId,
                BoothStatus.PUBLISHED
        ).map(boothMapper::toBoothResponseDTO)
         .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));

        return boothTour;
    }

    private ExhibitionResponseDTO getActiveExhibition(UUID exhibitionUuid) {
        ExhibitionResponseDTO exhibition = exhibitionService.getExhibitionByUuid(exhibitionUuid);

        if (!ExhibitionStatus.ACTIVE.name().equals(exhibition.getStatus())) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }
        return exhibition;
    }

    private VisitorProductSearchResponseDTO toVisitorProductResponse(
            Product product,
            List<ProductPlacementDTO> placements) {
        return new VisitorProductSearchResponseDTO(
                product.getId(),
                product.getCompany().getId(),
                product.getCompany().getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getSku(),
                product.getThumbnailUrl(),
                product.getPrice(),
                product.getCurrency(),
                placements);
    }

    private ProductPlacementDTO toProductPlacement(ProductPlacementProjection row) {
        return new ProductPlacementDTO(
                row.getBoothId(),
                row.getBoothName(),
                row.getBoothThumbnailUrl(),
                resolveListingPriority(row),
                row.getPanoramaId(),
                row.getPanoramaName(),
                row.getHotspotId());
    }

    private BoothListingPriority resolveListingPriority(ProductPlacementProjection row) {
        if (row.getListingPrioritySnapshot() != null) {
            return row.getListingPrioritySnapshot();
        }
        if (row.getTemplateListingPriority() != null) {
            return row.getTemplateListingPriority();
        }
        return BoothListingPriority.NORMAL;
    }
}
