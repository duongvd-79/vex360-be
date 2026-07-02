package com.example.vex360.features.product.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.vex360.features.product.dtos.response.ProductContentResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.shared.entities.Product;
import com.example.vex360.shared.entities.ProductContent;

@Component
public class ProductMapper {
    public ProductResponseDTO toResponse(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getCompany().getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getThumbnailUrl(),
                product.getStatus(),
                toContentResponses(product.getContents()));
    }

    private List<ProductContentResponseDTO> toContentResponses(List<ProductContent> contents) {
        return contents.stream()
                .sorted(Comparator.comparing(ProductContent::getOrderIndex))
                .map(content -> new ProductContentResponseDTO(
                        content.getId(),
                        content.getContentUrl(),
                        content.getType(),
                        content.getOrderIndex(),
                        content.getMimeType(),
                        content.getFileSize()))
                .toList();
    }
}
