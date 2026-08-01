package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.services.BoothReviewContentAssembler;
import com.example.vex360.features.company.entities.Company;

class BoothReviewContentAssemblerUnitTest {
    @Test
    void canceledReviewSummaryIncludesLifecycleDetails() {
        Instant canceledAt = Instant.parse("2026-01-10T00:00:00Z");
        Booth booth = Booth.builder().company(Company.builder().name("Company").build()).build();
        BoothReviewRequest request = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.CANCELED)
                .canceledAt(canceledAt)
                .cancellationReason("EXHIBITION_LIFECYCLE")
                .build();
        BoothReviewContentAssembler assembler = new BoothReviewContentAssembler(
                Mappers.getMapper(BoothMapper.class));

        var response = assembler.toRequestSummary(request, null);

        assertEquals(canceledAt, response.getCanceledAt());
        assertEquals("EXHIBITION_LIFECYCLE", response.getCancellationReason());
    }
}
