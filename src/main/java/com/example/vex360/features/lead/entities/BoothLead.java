package com.example.vex360.features.lead.entities;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.shared.enums.LeadStatus;
import com.example.vex360.features.user.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booth_leads", uniqueConstraints = @UniqueConstraint(name = "uk_booth_leads_booth_visitor", columnNames = {
        "booth_id", "visitor_user_id" }), indexes = {
                @Index(name = "idx_booth_leads_booth_status", columnList = "booth_id,status"),
                @Index(name = "idx_booth_leads_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoothLead {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    Booth booth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitor_user_id")
    User visitor;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "email", nullable = false)
    String email;

    @Column(name = "phone_number")
    String phoneNumber;

    @Column(name = "company_name")
    String companyName;

    @Column(name = "message", columnDefinition = "TEXT")
    String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(30)")
    @Builder.Default
    LeadStatus status = LeadStatus.NEW;

    @Column(name = "exhibitor_note", columnDefinition = "TEXT")
    String exhibitorNote;

    @Column(name = "consent_at")
    Instant consentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;

    @PrePersist
    void applyDefaults() {
        if (status == null) {
            status = LeadStatus.NEW;
        }
        if (consentAt == null) {
            consentAt = Instant.now();
        }
    }
}
