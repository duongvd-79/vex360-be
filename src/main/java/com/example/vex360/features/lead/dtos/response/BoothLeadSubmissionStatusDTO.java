package com.example.vex360.features.lead.dtos.response;

import java.time.Instant;

public record BoothLeadSubmissionStatusDTO(
        boolean submitted,
        Instant submittedAt) {
}
