package com.example.vex360.features.product.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductCategoryRequest {
    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String description;
}
