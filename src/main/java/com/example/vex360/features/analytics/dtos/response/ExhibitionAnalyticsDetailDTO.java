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
    private List<HourPoint> visitorsByHour;
    private List<PackageSummary> packages;
    /** Chỉ chứa số liệu lead tổng hợp; không trả thông tin nhận diện khách. */
    private LeadAnalytics leadAnalytics;

    /**
     * Số liệu tổng hợp ở góc nhìn ban tổ chức: tập trung vào mức độ lấp đầy gian
     * hàng, doanh thu bán gói và lượt khách vào triển lãm — cố ý KHÔNG lấy các chỉ
     * số cấp gian hàng (lượt xem/click từng booth) vì đó là thống kê của exhibitor.
     */
    @Data
    @Builder
    public static class Metrics {
        /** Số gian hàng đã được duyệt tham gia triển lãm. */
        private long approvedBoothCount;
        /** Số gian hàng dự kiến do organizer đặt ra khi tạo triển lãm (có thể null). */
        private Integer estimatedBooths;
        /** approvedBoothCount / estimatedBooths (%), 0 nếu chưa đặt chỉ tiêu. */
        private double boothFillRatePercent;
        /** Tổng doanh thu bán gói đã thanh toán trong kỳ (VND). */
        private long totalRevenue;
        /** Số lượt vào triển lãm (ENTER_EXHIBITION). */
        private long totalVisits;
        /** Số khách khác nhau đã vào triển lãm trong kỳ. */
        private long uniqueVisitorCount;
        /** Thời lượng trung bình mỗi lượt tham quan triển lãm (phút). */
        private double averageVisitDurationMinutes;
    }

    @Data
    @Builder
    public static class ChartPoint {
        private String date;
        private long visits;
        /** Doanh thu bán gói đã thanh toán trong ngày (VND). */
        private long revenue;
        private double averageVisitDurationMinutes;
    }

    /** Số khách khác nhau vào triển lãm theo giờ trong ngày (0-23). */
    @Data
    @Builder
    public static class HourPoint {
        private int hour;
        private long visitors;
    }

    @Data
    @Builder
    public static class PackageSummary {
        private String key;
        private String label;
        /** Số payment record trạng thái PAID; không phải số registration/gói duy nhất. */
        private long quantity;
        private long revenue;
    }

    @Data
    @Builder
    public static class LeadAnalytics {
        private long totalLeads;
        private long uniqueLeadVisitors;
        private long boothsWithLeads;
        /** Khách tạo lead duy nhất / khách tham quan duy nhất (%). */
        private double visitorToLeadRatePercent;
        /** Lead trạng thái CONVERTED / tổng lead (%). */
        private double conversionRatePercent;
        private long newCount;
        private long contactedCount;
        private long qualifiedCount;
        private long convertedCount;
        private long lostCount;
        private List<LeadDailyPoint> dailyTrend;
        private List<LeadBoothSummary> topBooths;
    }

    @Data
    @Builder
    public static class LeadDailyPoint {
        private String date;
        private long leads;
    }

    @Data
    @Builder
    public static class LeadBoothSummary {
        private String boothId;
        private String boothName;
        private long leadCount;
    }
}
