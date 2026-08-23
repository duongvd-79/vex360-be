package com.example.vex360.features.hall.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExhibitionHallRequest {
    @NotBlank(message = "Tên sảnh triển lãm không được để trống")
    @Size(max = 255, message = "Tên sảnh triển lãm không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả sảnh triển lãm không được vượt quá 5000 ký tự")
    private String description;
}
