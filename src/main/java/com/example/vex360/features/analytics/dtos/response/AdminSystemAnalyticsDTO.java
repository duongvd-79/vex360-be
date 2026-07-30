package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemAnalyticsDTO {
    private String startDate;
    private String endDate;
    private Metrics metrics;
    private List<DailyPoint> trend;
    private List<BreakdownItem> userRoles;
    private List<BreakdownItem> userStatuses;
    private List<BreakdownItem> exhibitionStatuses;
    private List<BreakdownItem> boothStatuses;
    private List<BreakdownItem> paymentStatuses;
    private List<BreakdownItem> leadStatuses;
    private List<BreakdownItem> designRequestStatuses;
    private List<TopExhibition> topExhibitions;

    @Data
    @Builder
    public static class Metrics {
        private long totalUsers;
        private long activeAccounts;
        private long newUsers;
        private long totalExhibitions;
        private long activeExhibitions;
        private long pendingExhibitions;
        private long totalBooths;
        private long publishedBooths;
        private long totalVisits;
        private long uniqueVisitors;
        private long boothViews;
        private long chatSessions;
        private long grossPaymentVolume;
        private long systemRevenue;
        private long paidTransactions;
        private long failedPayments;
        private long totalLeads;
        private long convertedLeads;
        private long totalDesignRequests;
        private long pendingDesignRequests;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private String date;
        private long newUsers;
        private long newExhibitions;
        private long visits;
        private long revenue;
        private long leads;
        private long designRequests;
    }

    @Data
    @Builder
    public static class BreakdownItem {
        private String key;
        private long value;
    }

    @Data
    @Builder
    public static class TopExhibition {
        private String id;
        private String name;
        private String status;
        private long visits;
        private long uniqueVisitors;
        private long revenue;
        private long leads;
    }
}
