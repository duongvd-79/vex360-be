package com.example.vex360.features.analytics.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.analytics.entities.AnalyticsEvent;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    // Mỗi dòng = 1 ngày: [ngày, số view, số visit, thời lượng TB (giây)]
    @Query(value = """
            SELECT DATE(event_time)                                                        AS day,
                   SUM(CASE WHEN event_type = 'BOOTH_VIEW' THEN 1 ELSE 0 END)              AS views,
                   SUM(CASE WHEN event_type = 'ENTER_EXHIBITION' THEN 1 ELSE 0 END)        AS visits,
                   AVG(CASE WHEN event_type = 'LEAVE_EXHIBITION' THEN duration_seconds END) AS avg_duration
            FROM analytics_events
            WHERE exhibition_id = :exhibitionId
              AND event_time BETWEEN :start AND :end
            GROUP BY DATE(event_time)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> aggregateDailyMetrics(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT AVG(duration_seconds)
            FROM analytics_events
            WHERE exhibition_id = :exhibitionId
              AND event_type = 'LEAVE_EXHIBITION'
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    Double averageVisitDurationSeconds(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

}
