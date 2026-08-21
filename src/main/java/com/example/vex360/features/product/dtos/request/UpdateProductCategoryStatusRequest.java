package com.example.vex360.features.product.dtos.request;

import com.example.vex360.features.product.enums.ProductCategoryStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductCategoryStatusRequest {
    @NotNull(message = "Trạng thái danh mục không được để trống")
    private ProductCategoryStatus status;
}
