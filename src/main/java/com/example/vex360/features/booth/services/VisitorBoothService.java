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
    /**
     * Returns a non-null page of published booths for an active exhibition.
     *
     * @param exhibitionUuid required exhibition identifier
     * @param keyword        optional booth-name search text
     * @param pageable       required paging and sorting request
     * @throws AppException when the exhibition does not exist or is not active
     */
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

    /**
     * Returns the non-null public tour detail of a published booth in an active
     * exhibition.
     *
     * @param exhibitionUuid required exhibition identifier
     * @param boothId        required booth identifier
     * @throws AppException when the exhibition does not exist, is not active, or
     *                      the booth is not found
     */
    BoothResponseDTO getBoothTourDetail(UUID exhibitionUuid, UUID boothId);

    Optional<Booth> findPublishedBoothByExhibitionUuidAndBoothId(UUID exhibitionUuid, UUID boothId);
}
