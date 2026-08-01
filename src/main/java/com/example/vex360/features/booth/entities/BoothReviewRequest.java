package com.example.vex360.features.booth.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "booth_review_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoothReviewRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Booth booth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    BoothReviewStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User submittedBy;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
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

    @Column(name = "change_summary_json", columnDefinition = "TEXT")
    String changeSummaryJson;

    @Column(name = "version_number")
    Integer versionNumber;

    @Column(name = "canceled_at")
    Instant canceledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    String cancellationReason;
}
