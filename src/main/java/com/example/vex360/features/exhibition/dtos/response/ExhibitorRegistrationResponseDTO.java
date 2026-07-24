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
    private String exhibitionName;
    private String participationReason;
    private String rejectedReason;
    private String reviewedByName;
}
