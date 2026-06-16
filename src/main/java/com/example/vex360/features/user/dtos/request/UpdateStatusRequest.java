package com.example.vex360.features.user.dtos.request;

import com.example.vex360.shared.enums.UserStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private UserStatus status;
}
