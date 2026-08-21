package com.example.vex360.features.designrequest.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectDesignDraftRequest {
    @NotBlank(message = "Ghi chú review không được để trống")
    @Size(max = 2000, message = "Ghi chú review không được vượt quá 2000 ký tự")
    private String reviewNote;
}
