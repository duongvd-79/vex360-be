package com.example.vex360.features.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.analytics.services.AdminAnalyticsService;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.lead.repositories.BoothLeadRepository;
import com.example.vex360.features.user.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExhibitionRepository exhibitionRepository;
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BoothLeadRepository boothLeadRepository;
    @Mock
    private DesignRequestRepository designRequestRepository;

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsService(
                userRepository,
                exhibitionRepository,
                boothRepository,
                analyticsEventRepository,
                paymentRepository,
                boothLeadRepository,
                designRequestRepository);
    }

    @Test
    void getSummaryAggregatesPeriodAndLifetimeMetrics() {
        when(userRepository.countGroupedByRole()).thenReturn(List.<Object[]>of(
                new Object[] { "VISITOR", 12L },
                new Object[] { "ORGANIZER", 2L }));
        when(userRepository.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "ACTIVE", 11L },
                new Object[] { "BLOCKED", 3L }));
        when(exhibitionRepository.countExhibitionsByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "ACTIVE", 2L },
                new Object[] { "PENDING", 1L }));
        when(boothRepository.countBoothsGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "PUBLISHED", 8L },
                new Object[] { "DRAFT", 2L }));
        when(paymentRepository.countAdminPaymentsByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] { "PAID", 4L },
                new Object[] { "FAILED", 1L }));
        when(boothLeadRepository.countAdminLeadsByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] { "NEW", 5L },
                new Object[] { "CONVERTED", 2L }));
        when(designRequestRepository.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { "PENDING", 3L },
                new Object[] { "APPROVED", 4L }));
        when(analyticsEventRepository.aggregateAdminPeriodMetrics(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 150L, 40L, 90L, 12L }));
        when(paymentRepository.aggregateAdminPaidMetrics(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 4L, 20_000_000L, 2_000_000L }));
        when(userRepository.aggregateDailyRegistrations(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-28", 3L }));
        when(exhibitionRepository.aggregateDailyCreated(any(), any())).thenReturn(List.of());
        when(paymentRepository.aggregateAdminDailyRevenue(any(), any())).thenReturn(List.of());
        when(designRequestRepository.aggregateDailyCreated(any(), any())).thenReturn(List.of());
        when(exhibitionRepository.findAll()).thenReturn(List.of());
        when(userRepository.count()).thenReturn(14L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(3L);
        when(exhibitionRepository.count()).thenReturn(3L);
        when(designRequestRepository.count()).thenReturn(7L);

        var result = service.getSummary(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 28));

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
