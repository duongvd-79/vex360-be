package com.example.vex360.features.exhibition.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitionReviewHistoryResponseDTO {
    UUID id;
    int versionNumber;
    ExhibitionReviewStatus status;
    Instant submittedAt;
    Instant reviewedAt;
    String reviewedByName;
    String rejectedReason;
    boolean legacyIncomplete;
    ExhibitionReviewSnapshotResponseDTO snapshot;
}
