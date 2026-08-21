package com.example.vex360.features.booth.dtos.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.product.enums.ProductStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewProductItemDTO {
    private UUID id;
    private String name;
    private String sku;
    private String description;
    private String thumbnailUrl;
    private BigDecimal price;
    private String currency;
    private ProductStatus status;
    private Integer usageCount;
    private List<BoothReviewContentPlacementDTO> placements;
    private List<BoothReviewProductContentItemDTO> contents;
}
