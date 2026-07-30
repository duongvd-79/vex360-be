package com.example.vex360.features.lead.dtos.response;

import java.time.Instant;
import java.util.UUID;

public record BoothLeadSubmissionResponseDTO(
        UUID id,
        boolean alreadySubmitted,
        Instant submittedAt) {
}
