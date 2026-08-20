package com.example.vex360.features.exhibition.dtos.request;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class CreateExhibitionRequest {
    @NotBlank(message = "Tên triển lãm không được để trống")
    @Size(max = 255, message = "Tên triển lãm không được vượt quá 255 ký tự.")
    private String name;

    @NotBlank(message = "Danh mục không được để trống")
    @Size(max = 255, message = "Danh mục không được vượt quá 255 ký tự.")
    private String category;

    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự.")
    private String description;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @FutureOrPresent(message = "Ngày bắt đầu phải là hiện tại hoặc tương lai")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;

    @NotNull(message = "Số gian hàng dự kiến không được để trống")
    @Min(value = 1, message = "Số gian hàng dự kiến phải lớn hơn 0")
    @Max(value = 2000, message = "Số gian hàng dự kiến không được vượt quá 2000")
    private Integer estimatedBooths;

    @Deprecated
    private List<@NotNull(message = "Gói dịch vụ không được để trống") @Valid ConfigureExhibitionPackageRequest> packages;

    private List<@Valid SponsorRequestDTO> sponsors;

    @JsonIgnore
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu.")
    public boolean isEndDateValid() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }

    @JsonIgnore
    @AssertTrue(message = "Thời gian triển lãm không được vượt quá 90 ngày.")
    public boolean isDurationValid() {
        return startDate == null || endDate == null
                || ChronoUnit.DAYS.between(startDate, endDate) <= 90;
    }
}
