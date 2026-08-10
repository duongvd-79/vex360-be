package com.example.vex360.features.packagetemplate.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageTemplateSelectionResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer maxProductsPerBooth;
    private Integer maxEmbeddedVideosPerBooth;
    private Integer maxPanoramasPerBooth;
    private Integer maxHotspotsPerBooth;
    private BoothListingPriority listingPriority;
    private PackageTemplateStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
