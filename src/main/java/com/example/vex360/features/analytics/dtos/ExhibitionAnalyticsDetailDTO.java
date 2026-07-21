package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExhibitionAnalyticsDetailDTO {
    private ExhibitionAnalyticsOverviewDTO exhibition;
    private String startDate;
    private String endDate;
    private boolean hasData;
    private String messageCode;
    private String message;
    private Metrics metrics;
    private List<ChartPoint> chart;
    private List<PackageSummary> packages;

    @Data
    @Builder
    public static class Metrics {
        private long totalViews;
        private long totalVisits;
        private long totalChats;
        private double averageVisitDurationMinutes;
    }

    @Data
    @Builder
    public static class ChartPoint {
        private String date;
        private long views;
        private long visits;
        private long chats;
        private double averageVisitDurationMinutes;
    }

    @Data
    @Builder
    public static class PackageSummary {
        private String key;
        private String label;
        private long quantity;
        private long revenue;
    }
}
