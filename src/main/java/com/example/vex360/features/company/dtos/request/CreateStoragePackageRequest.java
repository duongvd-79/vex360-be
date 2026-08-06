package com.example.vex360.features.company.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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

    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự.")
    private String description;

    @NotNull(message = "Dung lượng không được để trống")
    @Min(value = 1, message = "Dung lượng phải lớn hơn 0")
    @Max(value = 10_995_116_277_760L, message = "Dung lượng gói lưu trữ không được vượt quá 10TB.")
    private Long quotaBytes;

    @NotNull(message = "Giá không được để trống")
    @Min(value = 0, message = "Giá phải lớn hơn hoặc bằng 0")
    @Digits(integer = 13, fraction = 2, message = "Giá gói tối đa 13 chữ số nguyên và 2 chữ số thập phân.")
    private Long priceVnd;
}
