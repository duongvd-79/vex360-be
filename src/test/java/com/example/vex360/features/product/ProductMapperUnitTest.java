package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.mapper.ProductMapper;

class ProductMapperUnitTest {

    private final ProductMapper productMapper = new ProductMapper();

    @Test
    void toResponse_SortsContentsByOrderIndex() {
        ProductContent second = content(1, "https://example.com/second.mp4", ProductContentType.VIDEO);
        ProductContent first = content(0, "https://example.com/first.jpg", ProductContentType.IMAGE);
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .category(ProductCategory.builder().id(UUID.randomUUID()).name("Category").build())
                .description("Description")
                .contents(List.of(second, first))
                .build();

        ProductResponseDTO response = productMapper.toResponse(product);

        assertEquals(List.of(first.getId(), second.getId()),
                response.getContents().stream().map(content -> content.getId()).toList());
        assertEquals(ProductContentType.IMAGE, response.getContents().get(0).getType());
        assertEquals(ProductContentType.VIDEO, response.getContents().get(1).getType());
    }

    private ProductContent content(int orderIndex, String url, ProductContentType type) {
        return ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl(url)
                .type(type)
                .orderIndex(orderIndex)
                .mimeType(type == ProductContentType.IMAGE ? "image/jpeg" : "video/mp4")
                .fileSize(100L)
                .build();
    }
}
