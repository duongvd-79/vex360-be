package com.example.vex360.features.exhibition.dtos.request;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExhibitionRequest {
    @NotBlank(message = "Exhibition name is required")
    private String name;

    @NotBlank(message = "Category is required")
    private String category;

    private String description;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be in the present or future")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Estimated booths is required")
    @Min(value = 1, message = "Estimated booths must be at least 1")
    private Integer estimatedBooths;

    @Valid
    private java.util.List<ConfigureExhibitionPackageRequest> packages;
}
