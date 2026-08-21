package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarnBoothRequest {
    @NotBlank(message = "Lý do cảnh báo gian hàng là bắt buộc.")
    private String warningReason;
}
