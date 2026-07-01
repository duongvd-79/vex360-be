package com.example.vex360.features.booth.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.vex360.features.product.enums.ProductStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotProductSummaryDTO {
    private UUID id;
    private String name;
    private String sku;
    private String thumbnailUrl;
    private BigDecimal price;
    private String currency;
    private ProductStatus status;
}
