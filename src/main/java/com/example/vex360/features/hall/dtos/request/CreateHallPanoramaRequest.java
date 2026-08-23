package com.example.vex360.features.hall.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHallPanoramaRequest {
    @NotBlank(message = "Tên panorama không được để trống")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự")
    private String name;

    @PositiveOrZero(message = "Thứ tự panorama phải lớn hơn hoặc bằng 0")
    private Integer orderIndex;

    private Boolean isDefault;
}
