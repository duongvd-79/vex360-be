package com.example.vex360.features.product.mapper;

import org.springframework.stereotype.Component;

import com.example.vex360.features.product.dtos.response.ProductCategoryResponseDTO;
import com.example.vex360.shared.entities.ProductCategory;

@Component
public class ProductCategoryMapper {
    public ProductCategoryResponseDTO toResponse(ProductCategory category) {
        return new ProductCategoryResponseDTO(
                category.getId(),
                category.getCompany().getId(),
                category.getName(),
                category.getDescription(),
                category.getStatus());
    }
}
