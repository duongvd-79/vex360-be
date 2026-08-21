package com.example.vex360.shared.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteUploadRequest {
    @NotBlank(message = "Public ID không được để trống.")
    @Size(max = 500, message = "Public ID không được vượt quá 500 ký tự.")
    private String publicId;

    @NotBlank(message = "Loại tài nguyên không được để trống.")
    @Pattern(regexp = "^(image|video)$", message = "Loại tài nguyên chỉ được là image hoặc video.")
    private String resourceType;
}
