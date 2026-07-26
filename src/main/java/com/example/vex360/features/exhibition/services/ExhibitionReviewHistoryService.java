package com.example.vex360.features.exhibition.services;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewHistoryResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;
import com.example.vex360.features.user.entities.User;

public interface ExhibitionReviewHistoryService {

    void recordInitialSubmission(Exhibition exhibition, User submittedBy, String keyVisualUrl);

    void recordResubmissionOrUpdate(Exhibition exhibition, User submittedBy, String keyVisualUrl);

    void recordReviewResult(Exhibition exhibition, User admin, ExhibitionReviewStatus newStatus, String rejectedReason);

    List<ExhibitionReviewHistoryResponseDTO> getReviewHistoryForAdmin(UUID exhibitionUuid);

    List<ExhibitionReviewHistoryResponseDTO> getReviewHistoryForOrganizer(User organizer, UUID exhibitionUuid);
}
