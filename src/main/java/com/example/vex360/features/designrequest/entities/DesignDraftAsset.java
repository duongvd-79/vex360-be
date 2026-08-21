package com.example.vex360.features.designrequest.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;

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
@Table(
        name = "design_draft_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_design_draft_asset_request_public_id",
                columnNames = { "design_request_id", "public_id" }))
@Getter
@Setter
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
    DesignRequest designRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignDraftAssetType assetType = DesignDraftAssetType.PANORAMA;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_source", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignDraftAssetSource assetSource = DesignDraftAssetSource.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_state", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignDraftAssetQuotaState quotaState = DesignDraftAssetQuotaState.CHARGED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
