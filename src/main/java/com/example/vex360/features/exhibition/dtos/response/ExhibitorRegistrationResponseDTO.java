package com.example.vex360.features.exhibition.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExhibitorRegistrationResponseDTO {
    private Integer id;
    private UUID uuid;
    private Integer exhibitionPackageId;
    private UUID companyUserId;
    private String status;
    private Instant submittedAt;
    private Instant reservedUntil;
    private String checkoutUrl;
    private String paymentStatus;
    private Long orderCode;

    // Additional fields for Organizer management
    private String companyName;
    private String companyEmail;
    private String packageName;
    private BigDecimal priceSnapshot;
    private BigDecimal finalPriceSnapshot;
    private String currencySnapshot;
    private Integer maxProductsPerBoothSnapshot;
    private Integer maxEmbeddedVideosPerBoothSnapshot;
    private Integer maxPanoramasPerBoothSnapshot;
    private Integer maxHotspotsPerBoothSnapshot;
    private String exhibitionName;
    private String participationReason;
    private String boothName;
    private String boothDescription;
    private String rejectedReason;
    private String reviewedByName;
}
