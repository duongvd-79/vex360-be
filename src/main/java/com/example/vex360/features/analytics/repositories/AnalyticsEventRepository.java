package com.example.vex360.features.analytics.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.analytics.entities.AnalyticsEvent;
import com.example.vex360.features.analytics.enums.AnalyticsEventType;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    // Tổng số lượt visitor vào xem 1 triển lãm (toàn thời gian) -- hiển thị ở trang chi tiết công khai
    long countByExhibitionIdAndEventType(Integer exhibitionId, AnalyticsEventType eventType);

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
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT AVG(duration_seconds)
            FROM analytics_events
            WHERE exhibition_id = :exhibitionId
              AND event_type = 'LEAVE_EXHIBITION'
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    Double averageVisitDurationSeconds(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // ===== Thống kê cấp gian hàng (booth) cho exhibitor =====

    // Mỗi dòng = 1 ngày: [ngày, lượt xem, lượt click sản phẩm, lượt chat, thời lượng ở booth TB (giây)]
    @Query(value = """
            SELECT DATE(event_time)                                                        AS day,
                   SUM(CASE WHEN event_type = 'BOOTH_VIEW' THEN 1 ELSE 0 END)              AS views,
                   SUM(CASE WHEN event_type = 'PRODUCT_CLICK' THEN 1 ELSE 0 END)           AS product_clicks,
                   SUM(CASE WHEN event_type = 'CHAT_INITIATED' THEN 1 ELSE 0 END)          AS chats,
                   AVG(CASE WHEN event_type = 'BOOTH_LEAVE' THEN duration_seconds END)      AS avg_duration
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_time BETWEEN :start AND :end
            GROUP BY DATE(event_time)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> aggregateBoothDailyMetrics(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT AVG(duration_seconds)
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_type = 'BOOTH_LEAVE'
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    Double averageBoothDurationSeconds(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Lấy metadata_json của mọi lượt click (product/hotspot) để gom theo tên clickable ở tầng service
    @Query(value = """
            SELECT metadata_json
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_type IN ('PRODUCT_CLICK', 'HOTSPOT_CLICK')
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    List<String> findBoothClickMetadata(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Lấy danh sách thời lượng từng lượt rời booth để chia bucket histogram ở tầng service
    @Query(value = """
            SELECT duration_seconds
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_type = 'BOOTH_LEAVE'
              AND duration_seconds IS NOT NULL
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    List<Integer> findBoothLeaveDurations(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Mỗi dòng = 1 giờ trong ngày (0-23): [giờ, số lượt xem]
    @Query(value = """
            SELECT HOUR(event_time) AS hour_of_day,
                   COUNT(*)          AS views
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_type = 'BOOTH_VIEW'
              AND event_time BETWEEN :start AND :end
            GROUP BY HOUR(event_time)
            ORDER BY hour_of_day
            """, nativeQuery = true)
    List<Object[]> aggregateBoothViewsByHour(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // ===== Thống kê tổng hợp nhiều gian hàng (dashboard exhibitor) =====

    // Mỗi dòng = 1 ngày: [ngày, tổng lượt xem, tổng lượt tương tác] gộp mọi gian hàng của công ty
    @Query(value = """
            SELECT DATE(event_time)                                                           AS day,
                   SUM(CASE WHEN event_type = 'BOOTH_VIEW' THEN 1 ELSE 0 END)                 AS views,
                   SUM(CASE WHEN event_type IN ('PRODUCT_CLICK', 'HOTSPOT_CLICK') THEN 1
                            ELSE 0 END)                                                       AS interactions
            FROM analytics_events
            WHERE booth_id IN (:boothIds)
              AND event_time BETWEEN :start AND :end
            GROUP BY DATE(event_time)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> aggregateCompanyDailyMetrics(
            @Param("boothIds") List<UUID> boothIds,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Tổng lượt xem của 1 gian hàng trong khoảng thời gian -- dùng để xếp hạng gian hàng nổi bật
    @Query(value = """
            SELECT COUNT(*)
            FROM analytics_events
            WHERE booth_id = :boothId
              AND event_type = 'BOOTH_VIEW'
              AND event_time BETWEEN :start AND :end
            """, nativeQuery = true)
    long countBoothViews(
            @Param("boothId") UUID boothId,
            @Param("start") Instant start,
            @Param("end") Instant end);

}
