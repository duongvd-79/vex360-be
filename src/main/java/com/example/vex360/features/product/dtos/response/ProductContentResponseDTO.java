package com.example.vex360.features.product.dtos.response;

import java.util.UUID;

import com.example.vex360.features.product.enums.ProductContentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductContentResponseDTO {
    private UUID id;
    private String contentUrl;
    private ProductContentType type;
    private Integer orderIndex;
    private String mimeType;
    private Long fileSize;
}
