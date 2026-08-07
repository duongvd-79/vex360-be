package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMediaAssetRequest {
    @NotBlank(message = "Ten media khong duoc de trong")
    @Size(max = 255, message = "Tên media không được vượt quá 255 ký tự.")
    private String name;
}
