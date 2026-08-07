package com.example.vex360.features.exhibition.dtos.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconcileExhibitionPackagesRequest {

    @NotNull
    @Size(min = 1, max = 3)
    private List<@Valid PackageSelection> packages;

    public enum SelectionType {
        EXISTING,
        TEMPLATE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PackageSelection {
        @NotNull
        private SelectionType type;

        private Integer exhibitionPackageId;
        private UUID templateId;
        private Integer replacesExhibitionPackageId;

        @NotNull
        @DecimalMin(value = "0.0", inclusive = true)
        @Digits(integer = 13, fraction = 2)
        private BigDecimal finalPrice;
    }
}
