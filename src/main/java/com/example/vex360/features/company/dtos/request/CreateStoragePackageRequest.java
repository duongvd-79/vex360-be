package com.example.vex360.features.company.dtos.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStoragePackageRequest {

    @NotBlank(message = "Tên gói không được để trống")
    @Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "Dung lượng không được để trống")
    @Min(value = 1, message = "Dung lượng phải lớn hơn 0")
    private Long quotaBytes;

    @NotNull(message = "Giá không được để trống")
    @Min(value = 0, message = "Giá phải lớn hơn hoặc bằng 0")
    private Long priceVnd;
}
