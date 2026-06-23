package com.example.vex360.features.booth.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "hotspots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Hotspot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "name", nullable = false)
    String name;

    // Panorama dang chua hotspot nay.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_panorama_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Panorama sourcePanorama;

    // Panorama se duoc mo khi nguoi dung bam hotspot.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_panorama_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Panorama targetPanorama;

    @Column(name = "x_position", nullable = false)
    Double xPosition;

    @Column(name = "y_position", nullable = false)
    Double yPosition;

    @Column(name = "z_position", nullable = false)
    Double zPosition;
}
