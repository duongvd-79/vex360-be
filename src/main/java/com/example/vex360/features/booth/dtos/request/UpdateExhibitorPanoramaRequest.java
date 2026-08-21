package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExhibitorPanoramaRequest {
    @Pattern(regexp = "^(?=.*\\S).+$", message = "Tên panorama không được để trống nếu được cung cấp.")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự.")
    private String name;

    @PositiveOrZero(message = "Thứ tự panorama phải lớn hơn hoặc bằng 0.")
    private Integer orderIndex;

    private Boolean isDefault;
}
