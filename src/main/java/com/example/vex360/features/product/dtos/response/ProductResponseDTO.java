package com.example.vex360.features.product.dtos.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.product.enums.ProductStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private UUID id;
    private UUID companyId;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private String currency;
    private String thumbnailUrl;
    private ProductStatus status;
    private List<ProductContentResponseDTO> contents;
}
