package com.example.vex360.features.product.dtos.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductContentRequest {
    @NotBlank(message = "Mã file nội dung không được để trống")
    private String fileKey;

    @NotNull(message = "Thứ tự nội dung không được để trống")
    @Min(0)
    private Integer orderIndex;
}
