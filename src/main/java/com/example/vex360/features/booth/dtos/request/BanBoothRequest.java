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
public class BanBoothRequest {
    @NotBlank(message = "Lý do khóa gian hàng (BAN) là bắt buộc.")
    private String banReason;
}
