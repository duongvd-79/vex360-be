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
public class RejectExhibitorRegistrationRequest {
    @NotBlank(message = "Ly do tu choi khong duoc de trong")
    @Size(max = 1000, message = "Ly do tu choi khong duoc vuot qua 1000 ky tu")
    private String rejectedReason;
}
