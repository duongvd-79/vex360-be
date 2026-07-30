package com.example.vex360.features.designrequest.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.example.vex360.features.booth.entities.MediaAsset;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "design_request_media_assets", uniqueConstraints = @UniqueConstraint(
        name = "uk_design_request_media_asset", columnNames = { "design_request_id", "media_asset_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignRequestMediaAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_request_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    DesignRequest designRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    MediaAsset mediaAsset;

    @Column(name = "required_from_baseline", nullable = false)
    @Builder.Default
    Boolean requiredFromBaseline = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
