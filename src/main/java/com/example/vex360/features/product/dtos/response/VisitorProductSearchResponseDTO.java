package com.example.vex360.features.product.dtos.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitorProductSearchResponseDTO {
    private UUID id;
    private UUID companyId;
    private String companyName;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private String sku;
    private String thumbnailUrl;
    private BigDecimal price;
    private String currency;
    private List<ProductPlacementDTO> placements;
}
