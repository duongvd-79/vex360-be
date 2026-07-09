package com.example.vex360.features.exhibition.services;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;

public interface ExhibitorRegistrationService {
        ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId);

        ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId);

        PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForOrganizer(
                        User organizer, UUID exhibitionUuid, ExhibitorRegistrationStatus status, String keyword,
                        Pageable pageable);

        PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForExhibitor(
                        User exhibitor, ExhibitorRegistrationStatus status, String keyword, Pageable pageable);

        ExhibitorRegistrationResponseDTO approveRegistration(User organizer, UUID registrationUuid);

        ExhibitorRegistrationResponseDTO rejectRegistration(User organizer, UUID registrationUuid,
                        String rejectedReason);
}
