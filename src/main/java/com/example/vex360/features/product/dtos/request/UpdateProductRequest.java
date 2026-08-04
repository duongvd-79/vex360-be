package com.example.vex360.features.product.dtos.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.product.enums.ProductStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {
    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @NotBlank(message = "Mã sản phẩm không được để trống")
    private String sku;

    @NotNull(message = "Danh mục sản phẩm không được để trống")
    private UUID categoryId;

    @NotBlank(message = "Mô tả sản phẩm không được để trống")
    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    private String currency;

    @NotNull(message = "Trang thai san pham khong duoc de trong")
    private ProductStatus status;

    private String thumbnailUrl;
    private String thumbnailPublicId;
    private Long thumbnailFileSize;

    @JsonIgnore
    @AssertTrue(message = "URL, Public ID và kích thước ảnh đại diện phải được cung cấp cùng nhau.")
    public boolean isThumbnailMetadataConsistent() {
        int count = 0;
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) count++;
        if (thumbnailPublicId != null && !thumbnailPublicId.isBlank()) count++;
        if (thumbnailFileSize != null) count++;
        return count == 0 || count == 3;
    }

    private List<UUID> existingContentIds;

    private List<@Valid CreateProductContentPreUploadedRequest> newContents;
}
