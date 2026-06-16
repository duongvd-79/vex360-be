package com.example.vex360.features.user.dtos.request;

import com.example.vex360.shared.enums.Role;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {
    @NotNull(message = "Role không được để trống")
    private Role role;
}
