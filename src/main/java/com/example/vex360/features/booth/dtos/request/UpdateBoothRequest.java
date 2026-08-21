package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothRequest {
    @Pattern(regexp = "^(?=.*\\S).+$", message = "Tên booth không được để trống nếu được cung cấp.")
    @Size(max = 255, message = "Tên booth không được vượt quá 255 ký tự.")
    private String name;
    @Size(max = 5000, message = "Mô tả booth không được vượt quá 5000 ký tự.")
    private String description;
    @Size(max = 100, message = "Display template key không được vượt quá 100 ký tự.")
    private String displayTemplateKey;
}
