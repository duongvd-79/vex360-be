package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Thống kê chi tiết của một gian hàng (booth) cho exhibitor.
 * Tương tự ExhibitionAnalyticsDetailDTO nhưng chỉ số hướng về gian hàng.
 */
@Data
@Builder
public class BoothAnalyticsDetailDTO {
    private BoothSummary booth;
    private String startDate;
    private String endDate;
    private boolean hasData;
    private String messageCode;
    private String message;
    private Metrics metrics;
    // Lead được ghi nhận trong đúng khoảng ngày đang lọc, phân theo trạng thái hiện tại.
    private LeadMetrics leadMetrics;
    private List<ChartPoint> chart;
    // Xếp hạng clickable (hotspot/sản phẩm) theo số lượt click, nhiều nhất trước
    private List<ClickableRank> topClickables;
    // Phân bố thời gian khách ở trong booth theo các khoảng
    private List<DurationBucket> durationHistogram;
    // Lượt xem theo từng giờ trong ngày (0-23)
    private List<HourPoint> viewsByHour;

    @Data
    @Builder
    public static class BoothSummary {
        private String id;
        private String name;
        private String status;
    }

    @Data
    @Builder
    public static class Metrics {
        private long totalViews;
        private long totalProductClicks;
        private long totalChats;
        private double averageTimeInBoothMinutes;
    }

    @Data
    @Builder
    public static class LeadMetrics {
        private long total;
        private long newCount;
        private long contactedCount;
        private long qualifiedCount;
        private long convertedCount;
        private long lostCount;
        private double conversionRate;
    }

    @Data
    @Builder
    public static class ChartPoint {
        private String date;
        private long views;
        private long productClicks;
        private long chats;
        private double averageTimeInBoothMinutes;
    }

    @Data
    @Builder
    public static class ClickableRank {
        private String name;
        private long clicks;
    }

    @Data
    @Builder
    public static class DurationBucket {
        private String label;
        private long count;
    }

    @Data
    @Builder
    public static class HourPoint {
        private int hour;
        private long views;
    }
}
