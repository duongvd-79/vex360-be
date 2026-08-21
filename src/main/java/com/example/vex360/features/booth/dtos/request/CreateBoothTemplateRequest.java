package com.example.vex360.features.booth.dtos.request;

import java.util.List;

import com.example.vex360.features.booth.enums.BoothStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoothTemplateRequest {
    @NotBlank(message = "Tên booth không được để trống")
    @Size(max = 255, message = "Tên booth không được vượt quá 255 ký tự.")
    private String name;

    @Size(max = 5000, message = "Mô tả booth không được vượt quá 5000 ký tự.")
    private String description;

    private BoothStatus status;

    @NotEmpty(message = "Booth template cần ít nhất 1 Panorama")
    private List<@Valid CreatePanoramaRequest> panoramas;
}
