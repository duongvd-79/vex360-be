package com.example.vex360.features.hall.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.hall.enums.HallStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExhibitionHallResponseDTO {
    private UUID id;
    private UUID exhibitionUuid;
    private String name;
    private String description;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
    private HallStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
