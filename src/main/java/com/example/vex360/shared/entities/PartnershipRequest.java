package com.example.vex360.shared.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;

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
@Table(name = "partnership_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PartnershipRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User submittedByUser;

    @Column(name = "requester_name", nullable = false)
    String requesterName;

    @Column(name = "requester_email", nullable = false)
    String requesterEmail;

    @Column(name = "requester_phone_number", nullable = false)
    String requesterPhoneNumber;

    @Column(name = "organization_name", nullable = false)
    String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false)
    Role requestedRole;

    @Column(name = "message", columnDefinition = "TEXT")
    String message;

    @Column(name = "accepted_policy", nullable = false)
    Boolean acceptedPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    PartnershipRequestStatus status = PartnershipRequestStatus.PENDING;

    @Column(name = "review_note", columnDefinition = "TEXT")
    String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;
}
