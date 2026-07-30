package com.example.vex360.features.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.analytics.services.AdminAnalyticsService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.features.user.services.UserService;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private BoothReviewService boothReviewService;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private BoothLeadService boothLeadService;
    @Mock
    private DesignRequestService designRequestService;

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsService(
                userService,
                exhibitionService,
                boothReviewService,
                analyticsEventRepository,
                boothLeadService,
                designRequestService);
    }

    @Test
    void getSummaryAggregatesPeriodAndLifetimeMetrics() {
        when(userService.countGroupedByRole()).thenReturn(List.<Object[]>of(
                new Object[] { "VISITOR", 12L },
                new Object[] { "ORGANIZER", 2L }));
        when(userService.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "ACTIVE", 11L },
                new Object[] { "BLOCKED", 3L }));
        when(exhibitionService.countExhibitionsByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "ACTIVE", 2L },
                new Object[] { "PENDING", 1L }));
        when(boothReviewService.countBoothsGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "PUBLISHED", 8L },
                new Object[] { "DRAFT", 2L }));
        when(exhibitionService.countAdminPaymentsByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] { "PAID", 4L },
                new Object[] { "FAILED", 1L }));
        when(boothLeadService.countAdminLeadsByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] { "NEW", 5L },
                new Object[] { "CONVERTED", 2L }));
        when(designRequestService.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "PENDING", 3L },
                new Object[] { "APPROVED", 4L }));
        when(analyticsEventRepository.aggregateAdminPeriodMetrics(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 150L, 40L, 90L, 12L }));
        when(exhibitionService.aggregateAdminPaidMetrics(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 4L, 20_000_000L, 2_000_000L }));
        when(userService.aggregateDailyRegistrations(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-28", 3L }));
        when(exhibitionService.aggregateDailyCreatedExhibitions(any(), any())).thenReturn(List.of());
        when(exhibitionService.aggregateAdminDailyRevenue(any(), any())).thenReturn(List.of());
        when(designRequestService.aggregateDailyCreated(any(), any())).thenReturn(List.of());
        when(exhibitionService.getAllExhibitions()).thenReturn(List.of());
        when(userService.countUsers()).thenReturn(14L);
        when(userService.countByCreatedAtBetween(any(), any())).thenReturn(3L);
        when(exhibitionService.countExhibitions()).thenReturn(3L);
        when(designRequestService.countDesignRequests()).thenReturn(7L);

        var result = service.getSummary(
                LocalDate.of(2026, Month.JULY, 1),
                LocalDate.of(2026, Month.JULY, 28));

        assertThat(result.getStartDate()).isEqualTo("2026-07-01");
        assertThat(result.getEndDate()).isEqualTo("2026-07-28");
        assertThat(result.getMetrics().getTotalUsers()).isEqualTo(14L);
        assertThat(result.getMetrics().getActiveAccounts()).isEqualTo(11L);
        assertThat(result.getMetrics().getTotalBooths()).isEqualTo(10L);
        assertThat(result.getMetrics().getTotalVisits()).isEqualTo(150L);
        assertThat(result.getMetrics().getSystemRevenue()).isEqualTo(2_000_000L);
        assertThat(result.getMetrics().getTotalLeads()).isEqualTo(7L);
        assertThat(result.getMetrics().getPendingDesignRequests()).isEqualTo(3L);
        assertThat(result.getTrend()).singleElement()
                .satisfies(point -> {
                    assertThat(point.getDate()).isEqualTo("2026-07-28");
                    assertThat(point.getNewUsers()).isEqualTo(3L);
                });
        assertThat(result.getUserRoles()).hasSize(2);
        assertThat(result.getTopExhibitions()).isEmpty();
    }
}
