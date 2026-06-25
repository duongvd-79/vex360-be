package com.example.vex360.features.exhibition.services;

import java.util.UUID;

import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.shared.entities.User;

public interface ExhibitionService {
    ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request);
    ExhibitionResponseDTO getExhibitionByUuid(UUID uuid);
}
