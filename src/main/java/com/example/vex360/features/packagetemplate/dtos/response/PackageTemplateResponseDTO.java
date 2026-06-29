package com.example.vex360.features.packagetemplate.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageTemplateResponseDTO {
    private UUID id;
    private UUID createdById;
    private String createdByEmail;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer maxProductsPerBooth;
    private Integer maxEmbeddedVideosPerBooth;
    private Integer maxPanoramasPerBooth;
    private Integer maxHotspotsPerBooth;
    private Long storageLimitMb;
    private BoothListingPriority listingPriority;
    private PackageTemplateStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
