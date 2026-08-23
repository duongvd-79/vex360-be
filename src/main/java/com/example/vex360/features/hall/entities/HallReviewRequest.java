package com.example.vex360.features.hall.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.hall.enums.HallReviewStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "hall_review_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hall_review_requests_version", columnNames = { "hall_id", "version_number" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HallReviewRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    ExhibitionHall hall;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    HallReviewStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    User submittedBy;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    User reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    String rejectedReason;

    @Column(name = "content_snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    String contentSnapshotJson;

    @Column(name = "change_summary_json", nullable = false, columnDefinition = "LONGTEXT")
    String changeSummaryJson;

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;
}
