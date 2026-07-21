package com.example.vex360.features.analytics.entities;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "analytics_events", indexes = {
        // Tăng tốc query tổng hợp lọc theo triển lãm + khoảng thời gian
        @Index(name = "idx_analytics_exhibition_time", columnList = "exhibition_id, event_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id")
    Booth booth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    AnalyticsEventType eventType;

    @Column(name = "duration_seconds")
    Integer durationSeconds;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    String metadataJson;

    @CreationTimestamp
    @Column(name = "event_time", updatable = false)
    LocalDateTime eventTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id")
    Exhibition exhibition;

}
