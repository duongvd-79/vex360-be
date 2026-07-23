package com.example.vex360.features.designrequest.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "design_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    Booth booth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_designer_user_id")
    User assignedDesigner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignRequestStatus status = DesignRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, columnDefinition = "VARCHAR(50)")
    DesignRequestMode mode;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "review_note", columnDefinition = "TEXT")
    String reviewNote;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    Integer reviewCount = 0;

    @Column(name = "quota_charged", nullable = false)
    @Builder.Default
    Boolean quotaCharged = true;

    @Column(name = "revision_queued_at")
    Instant revisionQueuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignRequestCancellationStatus cancellationStatus = DesignRequestCancellationStatus.NONE;

    @Column(name = "cancellation_reason", length = 2000)
    String cancellationReason;

    @Column(name = "cancellation_requested_at")
    Instant cancellationRequestedAt;

    @Column(name = "cancellation_resolved_at")
    Instant cancellationResolvedAt;

    @Column(name = "cancellation_resolution_note", length = 2000)
    String cancellationResolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;

    @Column(name = "assigned_at")
    Instant assignedAt;

    @Column(name = "approved_at")
    Instant approvedAt;

    @Column(name = "canceled_at")
    Instant canceledAt;

    @OneToMany(mappedBy = "designRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<DesignDraft> drafts = new ArrayList<>();

    @OneToMany(mappedBy = "designRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<DesignRequestProduct> products = new ArrayList<>();

    @OneToMany(mappedBy = "designRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<DesignRequestMessage> messages = new ArrayList<>();
}
