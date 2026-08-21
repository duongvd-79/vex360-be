package com.example.vex360.features.exhibition.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorRequestDTO {
    @NotBlank(message = "Tên nhà tài trợ không được để trống")
    @Size(max = 255, message = "Tên nhà tài trợ không được vượt quá 255 ký tự.")
    private String name;
}
