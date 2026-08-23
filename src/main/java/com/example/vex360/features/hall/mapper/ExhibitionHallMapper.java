package com.example.vex360.features.hall.mapper;

import org.springframework.stereotype.Component;

import com.example.vex360.features.hall.dtos.response.ExhibitionHallResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;

@Component
public class ExhibitionHallMapper {
    public ExhibitionHallResponseDTO toResponse(ExhibitionHall hall) {
        return ExhibitionHallResponseDTO.builder()
                .id(hall.getId())
                .exhibitionUuid(hall.getExhibition().getUuid())
                .name(hall.getName())
                .description(hall.getDescription())
                .backgroundMusicUrl(hall.getBackgroundMusicUrl())
                .backgroundMusicFileName(hall.getBackgroundMusicFileName())
                .backgroundMusicFileSize(hall.getBackgroundMusicFileSize())
                .status(hall.getStatus())
                .createdAt(hall.getCreatedAt())
                .updatedAt(hall.getUpdatedAt())
                .build();
    }
}
