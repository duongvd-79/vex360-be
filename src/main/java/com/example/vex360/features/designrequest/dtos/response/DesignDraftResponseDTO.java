package com.example.vex360.features.designrequest.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftResponseDTO {
    private UUID id;
    private Integer versionNumber;
    private String note;
    private LocalDateTime createdAt;
}
