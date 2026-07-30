package com.example.vex360.features.lead.dtos.response;

import com.example.vex360.shared.enums.LeadStatus;

import java.time.Instant;
import java.util.UUID;

public record BoothLeadResponseDTO(
        UUID id,
        UUID boothId,
        String boothName,
        UUID exhibitionId,
        String exhibitionName,
        UUID visitorId,
        String visitorAvatarUrl,
        String fullName,
        String email,
        String phoneNumber,
        String companyName,
        String message,
        LeadStatus status,
        String exhibitorNote,
        Instant consentAt,
        Instant createdAt,
        Instant updatedAt) {
}
