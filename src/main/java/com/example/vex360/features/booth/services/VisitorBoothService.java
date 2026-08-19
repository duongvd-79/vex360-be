package com.example.vex360.features.booth.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.dtos.response.VisitorProductSearchResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;

public interface VisitorBoothService {

    PageResponse<BoothResponseDTO> getPublishedBooths(
            UUID exhibitionUuid,
            String keyword,
            BoothListingPriority listingPriority,
            Pageable pageable);

    PageResponse<VisitorProductSearchResponseDTO> searchDisplayedProducts(
            UUID exhibitionUuid,
            String keyword,
            Pageable pageable);

    ProductResponseDTO getDisplayedProductDetail(UUID exhibitionUuid, UUID productId);

    BoothResponseDTO getBoothTourDetail(UUID exhibitionUuid, UUID boothId);

    Optional<Booth> findPublishedBoothByExhibitionUuidAndBoothId(UUID exhibitionUuid, UUID boothId);
}
