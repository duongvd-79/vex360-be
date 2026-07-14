package com.example.vex360.shared.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteUploadRequest {
    @NotBlank
    private String publicId;

    private String resourceType; // "image" hoặc "video"

    private long fileSize; // bytes, để deduct storage
}
