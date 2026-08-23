package com.example.vex360.features.hall.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "hall_published_revisions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hall_published_revisions_hall", columnNames = "hall_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HallPublishedRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false, unique = true)
    ExhibitionHall hall;

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;

    @Column(name = "content_snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    String contentSnapshotJson;

    @Column(name = "published_at", nullable = false)
    Instant publishedAt;
}
