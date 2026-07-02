package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMediaAssetRequest {
    @NotBlank(message = "Ten media khong duoc de trong")
    private String name;
}
