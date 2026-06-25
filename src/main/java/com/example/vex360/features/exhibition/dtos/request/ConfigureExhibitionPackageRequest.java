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
    @NotNull(message = "Package template ID is required")
    private UUID templateId;

    @NotNull(message = "Final price is required")
    @DecimalMin(value = "0.0", message = "Final price must be at least 0.0")
    private BigDecimal finalPrice;
}
