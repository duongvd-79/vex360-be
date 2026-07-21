package com.example.vex360.features.designrequest.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.vex360.shared.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignRequestMessageResponseDTO {
    private UUID id;
    private UUID requestId;
    private UUID senderId;
    private String senderName;
    private Role senderRole;
    private String message;
    private LocalDateTime createdAt;
}
