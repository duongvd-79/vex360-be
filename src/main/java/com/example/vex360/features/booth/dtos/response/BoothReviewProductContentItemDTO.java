package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import com.example.vex360.features.product.enums.ProductContentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewProductContentItemDTO {
    private UUID id;
    private ProductContentType type;
    private String url;
    private String mimeType;
    private Long fileSize;
    private Integer orderIndex;
}
