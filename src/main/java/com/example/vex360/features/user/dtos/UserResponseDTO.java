package com.example.vex360.features.user.dtos;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String avatarUrl;
    private String status;
}
