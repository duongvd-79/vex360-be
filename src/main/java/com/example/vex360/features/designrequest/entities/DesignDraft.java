package com.example.vex360.features.designrequest.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;

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
        name = "design_drafts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_design_draft_request_version",
                columnNames = { "design_request_id", "version_number" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_request_id", nullable = false)
    DesignRequest designRequest;

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "booth_name")
    String boothName;

    @Column(name = "booth_description", columnDefinition = "TEXT")
    String boothDescription;

    @Column(name = "display_template_key", length = 100)
    String displayTemplateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "thumbnail_action", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignDraftFileAction thumbnailAction = DesignDraftFileAction.KEEP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_asset_id")
    DesignDraftAsset thumbnailAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_music_action", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    DesignDraftFileAction backgroundMusicAction = DesignDraftFileAction.KEEP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_music_asset_id")
    DesignDraftAsset backgroundMusicAsset;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<DesignDraftPanorama> panoramas = new ArrayList<>();
}
