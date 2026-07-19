package com.example.vex360.features.designrequest.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
        name = "design_draft_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_design_draft_asset_request_public_id",
                columnNames = { "design_request_id", "public_id" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignDraftAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    DesignRequest designRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User uploadedBy;

    @Column(name = "url", nullable = false, length = 1000)
    String url;

    @Column(name = "public_id", nullable = false, length = 500)
    String publicId;

    @Column(name = "file_name", length = 500)
    String fileName;

    @Column(name = "mime_type", length = 100)
    String mimeType;

    @Column(name = "file_size", nullable = false)
    Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
