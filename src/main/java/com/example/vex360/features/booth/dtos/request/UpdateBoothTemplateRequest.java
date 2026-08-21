package com.example.vex360.features.booth.dtos.request;

import com.example.vex360.features.booth.enums.BoothStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothTemplateRequest {
    @Pattern(regexp = "^(?=.*\\S).+$", message = "Tên booth template không được để trống nếu được cung cấp.")
    @Size(max = 255, message = "Tên booth template không được vượt quá 255 ký tự.")
    private String name;
    @Size(max = 5000, message = "Mô tả booth template không được vượt quá 5000 ký tự.")
    private String description;
    private BoothStatus status;
}
