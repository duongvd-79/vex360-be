package com.example.vex360.features.exhibition.dtos.request;

import java.time.LocalDate;
import java.util.List;

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
    @NotBlank(message = "Tên triển lãm không được để trống")
    private String name;

    @NotBlank(message = "Danh mục không được để trống")
    private String category;

    private String description;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @FutureOrPresent(message = "Ngày bắt đầu phải là hiện tại hoặc tương lai")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;

    @NotNull(message = "Số gian hàng dự kiến không được để trống")
    @Min(value = 1, message = "Số gian hàng dự kiến phải lớn hơn 0")
    private Integer estimatedBooths;

    @Valid
    private List<ConfigureExhibitionPackageRequest> packages;
}
