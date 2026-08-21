package com.example.vex360.features.lead.dtos.response;

public record BoothLeadSummaryDTO(
        long total,
        long newCount,
        long contactedCount,
        long qualifiedCount,
        long convertedCount,
        long lostCount) {
}
