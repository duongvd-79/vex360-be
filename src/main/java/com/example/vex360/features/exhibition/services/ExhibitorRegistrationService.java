package com.example.vex360.features.exhibition.services;

import java.util.UUID;

import com.example.vex360.shared.entities.ExhibitorRegistration;

import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;

public interface ExhibitorRegistrationService {
    ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId);
    ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId);
}
