package com.example.vex360.features.exhibition.entities;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;
import com.example.vex360.features.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
    name = "exhibition_review_requests",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_exhibition_review_version", columnNames = {"exhibition_id", "version_number"})
    },
    indexes = {
        @Index(name = "idx_exhibition_review_version", columnList = "exhibition_id, version_number DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitionReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Exhibition exhibition;

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    ExhibitionReviewStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User submittedBy;

    @Column(name = "submitted_at")
    Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    String rejectedReason;

    @Column(name = "content_snapshot_json", columnDefinition = "TEXT")
    String contentSnapshotJson;

    @Builder.Default
    @Column(name = "legacy_incomplete", nullable = false)
    Boolean legacyIncomplete = false;
}
