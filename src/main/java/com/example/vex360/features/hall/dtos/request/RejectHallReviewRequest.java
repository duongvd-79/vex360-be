package com.example.vex360.features.hall.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectHallReviewRequest {
    @NotBlank(message = "Rejected reason is required")
    @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự.")
    private String rejectedReason;
}
