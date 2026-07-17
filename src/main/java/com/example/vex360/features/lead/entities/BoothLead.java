package com.example.vex360.features.lead.entities;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.user.entities.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booth_leads")
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
