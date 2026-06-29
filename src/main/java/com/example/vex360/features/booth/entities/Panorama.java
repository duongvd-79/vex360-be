package com.example.vex360.features.booth.entities;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "panoramas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Panorama {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Booth booth;

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
    @OrderBy("name ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Hotspot> hotspots = new ArrayList<>();
}
