package com.example.vex360.features.product.dtos.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {
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

    private Boolean isVisible;

    private List<@Valid CreateProductContentRequest> contents;
}
