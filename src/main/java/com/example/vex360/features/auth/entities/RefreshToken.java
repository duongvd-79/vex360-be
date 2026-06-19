package com.example.vex360.features.auth.entities;

import java.time.Instant;

import com.example.vex360.shared.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false, name = "expiry_date")
    private Instant expiryDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
