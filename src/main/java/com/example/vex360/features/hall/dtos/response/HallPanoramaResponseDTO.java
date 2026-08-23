package com.example.vex360.features.hall.dtos.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallPanoramaResponseDTO {
    private UUID id;
    private UUID hallId;
    private String name;
    private String imageUrl;
    private String imageKey;
    private Long fileSize;
    private Integer orderIndex;
    private Boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
