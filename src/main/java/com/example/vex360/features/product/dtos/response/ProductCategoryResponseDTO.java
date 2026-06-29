package com.example.vex360.features.product.dtos.response;

import java.util.UUID;

import com.example.vex360.features.product.enums.ProductCategoryStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryResponseDTO {
    private UUID id;
    private UUID companyId;
    private String name;
    private String description;
    private ProductCategoryStatus status;
}
