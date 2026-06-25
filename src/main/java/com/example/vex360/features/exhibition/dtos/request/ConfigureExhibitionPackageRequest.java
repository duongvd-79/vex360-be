package com.example.vex360.features.exhibition.dtos.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigureExhibitionPackageRequest {
    @NotNull(message = "Package template ID không được để trống")
    private UUID templateId;

    @NotNull(message = "Giá tiền không được để trống")
    @DecimalMin(value = "0.0", message = "Giá tiền phải lớn hơn 0")
    private BigDecimal finalPrice;
}
