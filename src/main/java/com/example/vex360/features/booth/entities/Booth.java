package com.example.vex360.features.booth.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.user.entities.User;

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
import jakarta.persistence.OneToOne;
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
@Table(name = "booths", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booths_exhibitor_registration", columnNames = "exhibitor_registration_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Booth {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    BoothStatus status = BoothStatus.DRAFT;

    @Column(name = "is_template", nullable = false)
    @Builder.Default
    Boolean isTemplate = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    Company company;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibitor_registration_id", unique = true)
    ExhibitorRegistration exhibitorRegistration;

    @Column(name = "thumbnail_url", length = 1000)
    String thumbnailUrl;

    @Column(name = "thumbnail_public_id", length = 500)
    String thumbnailPublicId;

    @Column(name = "background_music_url", length = 1000)
    String backgroundMusicUrl;

    @Column(name = "background_music_public_id", length = 500)
    String backgroundMusicPublicId;

    @Column(name = "background_music_file_name", length = 255)
    String backgroundMusicFileName;

    @Column(name = "late_edit_allowed_until")
    LocalDate lateEditAllowedUntil;

    @Column(name = "background_music_file_size")
    Long backgroundMusicFileSize;

    @Column(name = "display_template_key", length = 100)
    @Builder.Default
    String displayTemplateKey = "classic";

    @Column(name = "warning_count")
    @Builder.Default
    Integer warningCount = 0;

    @Column(name = "warning_reason", columnDefinition = "TEXT")
    String warningReason;

    @Column(name = "warned_at")
    Instant warnedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warned_by_id")
    User warnedBy;

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    String banReason;

    @Column(name = "banned_at")
    Instant bannedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banned_by_id")
    User bannedBy;

    @OneToMany(mappedBy = "booth", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<Panorama> panoramas = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
