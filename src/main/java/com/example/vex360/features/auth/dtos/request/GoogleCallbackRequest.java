package com.example.vex360.features.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCallbackRequest {

    @NotBlank(message = "Authorization code không được để trống")
    private String code;
}