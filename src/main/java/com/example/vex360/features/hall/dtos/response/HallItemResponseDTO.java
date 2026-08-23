package com.example.vex360.features.hall.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallItemResponseDTO {
    private UUID id;
    private UUID hallId;
    private String name;
    private String description;
    private MediaAssetResponseDTO mediaAsset;
    private Integer displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
