package com.example.vex360.features.analytics.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.analytics.dtos.response.AdminSystemAnalyticsDTO;
import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final UserService userService;
    private final ExhibitionService exhibitionService;
    private final BoothReviewService boothReviewService;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final BoothLeadService boothLeadService;
    private final DesignRequestService designRequestService;

    @Transactional(readOnly = true)
    public AdminSystemAnalyticsDTO getSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate rangeEnd = endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate rangeStart = startDate != null ? startDate : rangeEnd.minusDays(29);
        Instant start = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        List<Object[]> roleRows = userService.countGroupedByRole();
        List<Object[]> userStatusRows = userService.countGroupedByStatus();
        List<Object[]> exhibitionStatusRows = exhibitionService.countExhibitionsByStatus();
        List<Object[]> boothStatusRows = boothReviewService.countBoothsGroupedByStatus();
        List<Object[]> paymentStatusRows = exhibitionService.countAdminPaymentsByStatus(start, end);
        List<Object[]> leadStatusRows = boothLeadService.countAdminLeadsByStatus(start, end);
        List<Object[]> designStatusRows = designRequestService.countGroupedByStatus();

        Map<String, Long> userStatuses = toCountMap(userStatusRows);
        Map<String, Long> exhibitionStatuses = toCountMap(exhibitionStatusRows);
        Map<String, Long> boothStatuses = toCountMap(boothStatusRows);
        Map<String, Long> paymentStatuses = toCountMap(paymentStatusRows);
        Map<String, Long> leadStatuses = toCountMap(leadStatusRows);
        Map<String, Long> designStatuses = toCountMap(designStatusRows);

        Object[] traffic = firstRow(analyticsEventRepository.aggregateAdminPeriodMetrics(start, end));
        Object[] payment = firstRow(exhibitionService.aggregateAdminPaidMetrics(start, end));

        Map<String, AdminSystemAnalyticsDTO.DailyPoint> trend = new TreeMap<>();
        userService.aggregateDailyRegistrations(start, end)
                .forEach(row -> dailyPoint(trend, row[0]).setNewUsers(longValue(row[1])));
        exhibitionService.aggregateDailyCreatedExhibitions(start, end)
                .forEach(row -> dailyPoint(trend, row[0]).setNewExhibitions(longValue(row[1])));
        exhibitionService.aggregateAdminDailyRevenue(start, end)
                .forEach(row -> dailyPoint(trend, row[0]).setRevenue(longValue(row[1])));
        designRequestService.aggregateDailyCreated(start, end)
                .forEach(row -> dailyPoint(trend, row[0]).setDesignRequests(longValue(row[1])));

        List<Exhibition> exhibitions = exhibitionService.getAllExhibitions();
        List<Integer> exhibitionIds = exhibitions.stream().map(Exhibition::getId).toList();
        Map<Integer, long[]> exhibitionTraffic = new HashMap<>();
        Map<Integer, Long> exhibitionRevenue = new HashMap<>();
        Map<Integer, Long> exhibitionLeads = new HashMap<>();
        if (!exhibitionIds.isEmpty()) {
            analyticsEventRepository.aggregateOrganizerDailyMetrics(exhibitionIds, start, end)
                    .forEach(row -> dailyPoint(trend, row[0]).setVisits(longValue(row[1])));
            boothLeadService.aggregateDailyForExhibitions(exhibitionIds, start, end)
                    .forEach(row -> dailyPoint(trend, row[0]).setLeads(longValue(row[1])));
            analyticsEventRepository.aggregatePerformanceByExhibition(exhibitionIds, start, end)
                    .forEach(row -> exhibitionTraffic.put(
                            ((Number) row[0]).intValue(),
                            new long[] { longValue(row[1]), longValue(row[2]) }));
            exhibitionRevenue = exhibitionService.aggregateRevenueByExhibition(exhibitionIds, start, end);
            boothLeadService.aggregatePerformanceForExhibitions(exhibitionIds, start, end)
                    .forEach(row -> exhibitionLeads.put(
                            ((Number) row[0]).intValue(), longValue(row[1])));
        }

        Map<Integer, Long> finalExhibitionRevenue = exhibitionRevenue;
        List<AdminSystemAnalyticsDTO.TopExhibition> topExhibitions = exhibitions.stream()
                .map(exhibition -> {
                    long[] values = exhibitionTraffic.getOrDefault(exhibition.getId(), new long[2]);
                    return AdminSystemAnalyticsDTO.TopExhibition.builder()
                            .id(exhibition.getUuid().toString())
                            .name(exhibition.getName())
                            .status(exhibition.getStatus().name())
                            .visits(values[0])
                            .uniqueVisitors(values[1])
                            .revenue(finalExhibitionRevenue.getOrDefault(exhibition.getId(), 0L))
                            .leads(exhibitionLeads.getOrDefault(exhibition.getId(), 0L))
                            .build();
                })
                .sorted(Comparator.comparingLong(AdminSystemAnalyticsDTO.TopExhibition::getVisits)
                        .reversed()
                        .thenComparing(AdminSystemAnalyticsDTO.TopExhibition::getName))
                .limit(10)
                .toList();

        long totalBooths = boothStatuses.values().stream().mapToLong(Long::longValue).sum();
        long totalLeads = leadStatuses.values().stream().mapToLong(Long::longValue).sum();

        return AdminSystemAnalyticsDTO.builder()
                .startDate(rangeStart.toString())
                .endDate(rangeEnd.toString())
                .metrics(AdminSystemAnalyticsDTO.Metrics.builder()
                        .totalUsers(userService.countUsers())
                        .activeAccounts(userStatuses.getOrDefault(UserStatus.ACTIVE.name(), 0L))
                        .newUsers(userService.countByCreatedAtBetween(start, end))
                        .totalExhibitions(exhibitionService.countExhibitions())
                        .activeExhibitions(exhibitionStatuses.getOrDefault(ExhibitionStatus.ACTIVE.name(), 0L))
                        .pendingExhibitions(exhibitionStatuses.getOrDefault(ExhibitionStatus.PENDING.name(), 0L))
                        .totalBooths(totalBooths)
                        .publishedBooths(boothStatuses.getOrDefault("PUBLISHED", 0L))
                        .totalVisits(valueAt(traffic, 0))
                        .uniqueVisitors(valueAt(traffic, 1))
                        .boothViews(valueAt(traffic, 2))
                        .chatSessions(valueAt(traffic, 3))
                        .paidTransactions(valueAt(payment, 0))
                        .grossPaymentVolume(valueAt(payment, 1))
                        .systemRevenue(valueAt(payment, 2))
                        .failedPayments(paymentStatuses.getOrDefault(PaymentStatus.FAILED.name(), 0L))
                        .totalLeads(totalLeads)
                        .convertedLeads(leadStatuses.getOrDefault("CONVERTED", 0L))
                        .totalDesignRequests(designRequestService.countDesignRequests())
                        .pendingDesignRequests(designStatuses.getOrDefault(DesignRequestStatus.PENDING.name(), 0L))
                        .build())
                .trend(new ArrayList<>(trend.values()))
                .userRoles(toBreakdown(roleRows))
                .userStatuses(toBreakdown(userStatusRows))
                .exhibitionStatuses(toBreakdown(exhibitionStatusRows))
                .boothStatuses(toBreakdown(boothStatusRows))
                .paymentStatuses(toBreakdown(paymentStatusRows))
                .leadStatuses(toBreakdown(leadStatusRows))
                .designRequestStatuses(toBreakdown(designStatusRows))
                .topExhibitions(topExhibitions)
                .build();
    }

    private AdminSystemAnalyticsDTO.DailyPoint dailyPoint(
            Map<String, AdminSystemAnalyticsDTO.DailyPoint> points,
            Object rawDate) {
        String date = rawDate.toString();
        return points.computeIfAbsent(date, key -> AdminSystemAnalyticsDTO.DailyPoint.builder()
                .date(key)
                .build());
    }

    private List<AdminSystemAnalyticsDTO.BreakdownItem> toBreakdown(List<Object[]> rows) {
        return rows.stream()
                .map(row -> AdminSystemAnalyticsDTO.BreakdownItem.builder()
                        .key(row[0].toString())
                        .value(longValue(row[1]))
                        .build())
                .toList();
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> values = new HashMap<>();
        rows.forEach(row -> values.put(row[0].toString(), longValue(row[1])));
        return values;
    }

    private long valueAt(Object[] values, int index) {
        return values == null || values.length <= index ? 0L : longValue(values[index]);
    }

    private Object[] firstRow(List<Object[]> rows) {
        return rows == null || rows.isEmpty() ? new Object[0] : rows.getFirst();
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
