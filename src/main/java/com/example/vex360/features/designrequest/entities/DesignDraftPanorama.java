package com.example.vex360.features.designrequest.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "design_draft_panoramas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignDraftPanorama {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    DesignDraft draft;

    @Column(name = "client_key", nullable = false, length = 100)
    String clientKey;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "image_url", nullable = false, length = 1000)
    String imageUrl;

    @Column(name = "image_key", length = 500)
    String imageKey;

    @Column(name = "order_index", nullable = false)
    Integer orderIndex;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    Boolean isDefault = false;

    @OneToMany(mappedBy = "sourcePanorama", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<DesignDraftHotspot> hotspots = new ArrayList<>();
}
