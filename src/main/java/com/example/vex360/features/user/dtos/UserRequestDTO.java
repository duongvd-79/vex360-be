package com.example.vex360.features.user.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {
    private String email;
    private String password;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String avatarUrl;
    private Boolean isActive;
}
