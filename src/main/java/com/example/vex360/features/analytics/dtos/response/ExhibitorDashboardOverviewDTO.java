package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Thống kê tổng hợp cho bảng điều khiển exhibitor — gộp số liệu của TẤT CẢ
 * gian hàng thuộc công ty, khác với BoothAnalyticsDetailDTO (chi tiết 1 gian hàng).
 */
@Data
@Builder
public class ExhibitorDashboardOverviewDTO {
    private String startDate;
    private String endDate;
    private Metrics metrics;
    private List<ChartPoint> chart;
    // Top 3 gian hàng có nhiều lượt xem nhất trong kỳ, để exhibitor biết nên xem chi tiết gian nào
    private List<TopBooth> topBooths;

    @Data
    @Builder
    public static class Metrics {
        private long totalViews;
        private long totalInteractions;
        private long unreadMessages;
        private long activeBoothsCount;
        private long totalBoothsCount;
    }

    @Data
    @Builder
    public static class ChartPoint {
        private String date;
        private long views;
        private long interactions;
    }

    @Data
    @Builder
    public static class TopBooth {
        private String id;
        private String name;
        private String status;
        private long views;
    }
}
