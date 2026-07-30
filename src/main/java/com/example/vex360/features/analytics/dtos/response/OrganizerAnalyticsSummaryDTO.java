package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizerAnalyticsSummaryDTO {
    private String startDate;
    private String endDate;
    private String selectedExhibitionId;
    private Metrics metrics;
    private List<DailyPoint> trend;
    private List<ExhibitionPerformance> exhibitions;
    private List<PackageSummary> packages;
    private List<AlertItem> alerts;

    @Data
    @Builder
    public static class Metrics {
        private long exhibitionCount;
        private long activeExhibitionCount;
        private long approvedBoothCount;
        private long estimatedBoothCount;
        private double boothFillRatePercent;
        private long totalVisits;
        private long uniqueVisitorCount;
        private long totalRevenue;
        private long totalLeads;
        private long uniqueLeadVisitors;
        private double visitorToLeadRatePercent;
        private double averageVisitDurationMinutes;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private String date;
        private long visits;
        private long uniqueVisitors;
        private long revenue;
        private long leads;
        private long registrations;
        private double averageVisitDurationMinutes;
    }

    @Data
    @Builder
    public static class ExhibitionPerformance {
        private String id;
        private String code;
        private String name;
        private String status;
        private String location;
        private String startDate;
        private String endDate;
        private long boothCount;
        private long approvedBoothCount;
        private long estimatedBoothCount;
        private double boothFillRatePercent;
        private long totalVisits;
        private long uniqueVisitorCount;
        private long totalRevenue;
        private long totalLeads;
        private long uniqueLeadVisitors;
        private double visitorToLeadRatePercent;
        private double averageVisitDurationMinutes;
        private long pendingRegistrationCount;
    }

    @Data
    @Builder
    public static class PackageSummary {
        private String key;
        private String label;
        private long paidTransactionCount;
        private long revenue;
    }

    @Data
    @Builder
    public static class AlertItem {
        private String exhibitionId;
        private String exhibitionName;
        private String severity;
        private String code;
        private String message;
    }
}
