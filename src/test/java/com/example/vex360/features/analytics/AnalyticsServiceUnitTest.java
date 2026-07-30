package com.example.vex360.features.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.chat.services.ChatService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.shared.enums.LeadStatus;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceUnitTest {
        @Mock
        ExhibitionService exhibitionService;
        @Mock
        BoothReviewService boothReviewService;
        @Mock
        AnalyticsEventRepository analyticsEventRepository;
        @Mock
        UserService userService;
        @Mock
        ChatService chatService;
        @Mock
        CompanyService companyService;
        @Mock
        BoothLeadService boothLeadService;
        @Mock
        ProductService productService;

        @InjectMocks
        AnalyticsService analyticsService;

        User organizer;
        Exhibition exhibition;

        @BeforeEach
        void setUp() {
                organizer = User.builder().id(UUID.randomUUID()).build();
                exhibition = Exhibition.builder()
                                .id(1).uuid(UUID.randomUUID()).name("Demo")
                                .organizer(organizer)
                                .status(ExhibitionStatus.ACTIVE)
                                .estimatedBooths(10)
                                .startDate(LocalDate.now().minusDays(5))
                                .endDate(LocalDate.now())
                                .build();
        }

        @Test
        void getExhibitionAnalytics_notOwner_throwsUnauthorized() {
                User other = User.builder().id(UUID.randomUUID()).build();
                exhibition = Exhibition.builder().id(1).uuid(UUID.randomUUID())
                                .organizer(other).build();
                when(exhibitionService.findExhibitionEntityByUuid(any())).thenReturn(exhibition);

                AppException ex = assertThrows(AppException.class,
                                () -> analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null,
                                                null));

                assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        }

        @Test
        void getExhibitionAnalytics_withData_aggregatesCorrectly() {
                when(exhibitionService.findExhibitionEntityByUuid(any())).thenReturn(exhibition);
                when(boothReviewService.countBoothsByExhibitionId(1)).thenReturn(3L);
                when(analyticsEventRepository.aggregateDailyMetrics(anyInt(), any(), any()))
                                .thenReturn(List.of(
                                                new Object[] { "2026-06-01", 10L, 4L, 600.0 },
                                                new Object[] { "2026-06-02", 20L, 6L, 300.0 }));
                when(exhibitionService.aggregateDailyRevenueForExhibition(anyInt(), any(), any()))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { "2026-06-01", BigDecimal.valueOf(5_000_000) },
                                                new Object[] { "2026-06-02", BigDecimal.valueOf(3_000_000) }));
                when(exhibitionService.countApprovedRegistrationsForExhibition(anyInt()))
                                .thenReturn(4L);
                when(analyticsEventRepository.averageVisitDurationSeconds(anyInt(), any(), any()))
                                .thenReturn(450.0);
                when(analyticsEventRepository.countUniqueVisitors(anyInt(), any(), any()))
                                .thenReturn(7L);
                when(exhibitionService.aggregatePaidPackageRevenueForExhibition(anyInt(), any(), any()))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { "Gói Cơ Bản", 2L, BigDecimal.valueOf(10_000_000) }));
                when(boothLeadService.countByStatusForExhibitionInRange(anyInt(), any(), any()))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { LeadStatus.NEW, 2L },
                                                new Object[] { LeadStatus.CONVERTED, 2L }));
                when(boothLeadService.countUniqueLeadVisitorsForExhibition(anyInt(), any(), any())).thenReturn(3L);
                when(boothLeadService.countBoothsWithLeadsForExhibition(anyInt(), any(), any())).thenReturn(2L);
                when(boothLeadService.aggregateDailyForExhibition(anyInt(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { "2026-06-01", 4L }));
                when(boothLeadService.findTopBoothsForExhibition(
                                anyInt(), any(), any(), any(org.springframework.data.domain.Pageable.class)))
                                .thenReturn(List.<Object[]>of(new Object[] { UUID.randomUUID(), "Mộc Việt", 4L }));

                var dto = analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null);

                assertTrue(dto.isHasData());
                assertEquals(10, dto.getMetrics().getTotalVisits());
                assertEquals(7, dto.getMetrics().getUniqueVisitorCount());
                assertEquals(7.5, dto.getMetrics().getAverageVisitDurationMinutes());
                assertEquals(8_000_000, dto.getMetrics().getTotalRevenue());
                assertEquals(4, dto.getMetrics().getApprovedBoothCount());
                assertEquals(10, dto.getMetrics().getEstimatedBooths());
                assertEquals(40.0, dto.getMetrics().getBoothFillRatePercent());
                assertEquals(2, dto.getChart().size());
                assertEquals("2026-06-01", dto.getChart().get(0).getDate());
                assertEquals(5_000_000, dto.getChart().get(0).getRevenue());
                assertEquals(1, dto.getPackages().size());
                assertEquals(10_000_000, dto.getPackages().get(0).getRevenue());
                assertEquals(4, dto.getLeadAnalytics().getTotalLeads());
                assertEquals(3, dto.getLeadAnalytics().getUniqueLeadVisitors());
                assertEquals(2, dto.getLeadAnalytics().getBoothsWithLeads());
                assertEquals(2, dto.getLeadAnalytics().getConvertedCount());
                assertEquals(50.0, dto.getLeadAnalytics().getConversionRatePercent());
                assertEquals(3 * 100.0 / 7, dto.getLeadAnalytics().getVisitorToLeadRatePercent());
                assertEquals("Mộc Việt", dto.getLeadAnalytics().getTopBooths().get(0).getBoothName());
        }

        @Test
        void getOrganizerSummary_aggregatesCrossExhibitionMetrics() {
                when(exhibitionService.getOrganizerExhibitions(organizer))
                                .thenReturn(List.of(exhibition));
                when(boothReviewService.countBoothsGroupedByExhibition(List.of(1)))
                                .thenReturn(Map.of(1, 4L));
                when(exhibitionService.countRegistrationsByStatusGroupedByExhibition(
                                List.of(1), ExhibitorRegistrationStatus.APPROVED))
                                .thenReturn(Map.of(1, 4L));
                when(exhibitionService.countRegistrationsByStatusGroupedByExhibition(
                                List.of(1), ExhibitorRegistrationStatus.PENDING))
                                .thenReturn(Map.of(1, 2L));
                when(analyticsEventRepository.aggregatePerformanceByExhibition(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { 1, 20L, 7L, 300.0 }));
                when(exhibitionService.aggregateRevenueByExhibition(any(), any(), any()))
                                .thenReturn(Map.of(1, 12_000_000L));
                when(boothLeadService.aggregatePerformanceForExhibitions(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { 1, 5L, 3L, 2L }));
                when(analyticsEventRepository.aggregateOrganizerDailyMetrics(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 20L, 7L, 300.0 }));
                when(exhibitionService.aggregateOrganizerDailyRevenue(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { "2026-07-01", BigDecimal.valueOf(12_000_000) }));
                when(boothLeadService.aggregateDailyForExhibitions(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 5L }));
                when(exhibitionService.aggregateDailyRegistrationSubmissions(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 2L }));
                when(exhibitionService.aggregateOrganizerPackageRevenue(any(), any(), any()))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { "Gói Tiêu chuẩn", 2L, BigDecimal.valueOf(12_000_000) }));
                when(analyticsEventRepository.countUniqueVisitorsForExhibitions(any(), any(), any())).thenReturn(7L);
                when(boothLeadService.countUniqueLeadVisitorsForExhibitions(any(), any(), any())).thenReturn(3L);
                when(analyticsEventRepository.averageVisitDurationSecondsForExhibitions(any(), any(), any()))
                                .thenReturn(300.0);

                var summary = analyticsService.getOrganizerSummary(
                                organizer, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

                assertEquals(1, summary.getMetrics().getExhibitionCount());
                assertEquals(4, summary.getMetrics().getApprovedBoothCount());
                assertEquals(40.0, summary.getMetrics().getBoothFillRatePercent());
                assertEquals(20, summary.getMetrics().getTotalVisits());
                assertEquals(7, summary.getMetrics().getUniqueVisitorCount());
                assertEquals(12_000_000, summary.getMetrics().getTotalRevenue());
                assertEquals(5, summary.getMetrics().getTotalLeads());
                assertEquals(3 * 100.0 / 7, summary.getMetrics().getVisitorToLeadRatePercent());
                assertEquals(5.0, summary.getMetrics().getAverageVisitDurationMinutes());
                assertEquals(1, summary.getTrend().size());
                assertEquals(2, summary.getTrend().get(0).getRegistrations());
                assertEquals(1, summary.getPackages().size());
                assertEquals(1, summary.getAlerts().size());
        }

        @Test
        void getBoothAnalytics_includesLeadMetricsForSelectedRange() {
                User exhibitor = User.builder().id(UUID.randomUUID()).build();
                UUID boothId = UUID.randomUUID();
                Booth booth = Booth.builder()
                                .id(boothId)
                                .name("Mộc Việt")
                                .createdBy(exhibitor)
                                .status(BoothStatus.PUBLISHED)
                                .build();

                when(boothReviewService.findBoothEntityById(boothId)).thenReturn(booth);
                when(analyticsEventRepository.aggregateBoothDailyMetrics(
                                org.mockito.ArgumentMatchers.eq(boothId), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.of());
                when(boothLeadService.countByStatusForBoothInRange(
                                org.mockito.ArgumentMatchers.eq(boothId), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { LeadStatus.NEW, 2L },
                                                new Object[] { LeadStatus.CONTACTED, 1L },
                                                new Object[] { LeadStatus.QUALIFIED, 1L },
                                                new Object[] { LeadStatus.CONVERTED, 1L }));

                var dto = analyticsService.getBoothAnalytics(
                                exhibitor, boothId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

                assertEquals(5, dto.getLeadMetrics().getTotal());
                assertEquals(2, dto.getLeadMetrics().getNewCount());
                assertEquals(1, dto.getLeadMetrics().getConvertedCount());
                assertEquals(20.0, dto.getLeadMetrics().getConversionRate());
        }

        @Test
        void getExhibitorDashboardOverview_includesCompanyLeadMetricsForSelectedRange() {
                User exhibitor = User.builder().id(UUID.randomUUID()).build();
                Company company = Company.builder().id(UUID.randomUUID()).build();

                when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
                when(boothReviewService.findCompanyBooths(
                                org.mockito.ArgumentMatchers.eq(company.getId()), any()))
                                .thenReturn(new PageImpl<>(List.of()));
                when(boothLeadService.countByStatusForCompanyInRange(
                                org.mockito.ArgumentMatchers.eq(company.getId()), any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(List.<Object[]>of(
                                                new Object[] { LeadStatus.NEW, 3L },
                                                new Object[] { LeadStatus.CONVERTED, 1L }));
                when(chatService.countUnreadForExhibitor(exhibitor.getId())).thenReturn(0L);

                var dto = analyticsService.getExhibitorDashboardOverview(
                                exhibitor, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

                assertEquals(4, dto.getMetrics().getTotalLeads());
                assertEquals(4, dto.getLeadMetrics().getTotal());
                assertEquals(3, dto.getLeadMetrics().getNewCount());
                assertEquals(1, dto.getLeadMetrics().getConvertedCount());
                assertEquals(25.0, dto.getLeadMetrics().getConversionRate());
        }

        @Test
        void getExhibitionAnalytics_noTrafficButHasApprovedBooths_stillReturnsFillRate() {
                when(exhibitionService.findExhibitionEntityByUuid(any())).thenReturn(exhibition);
                when(boothReviewService.countBoothsByExhibitionId(1)).thenReturn(2L);
                when(analyticsEventRepository.aggregateDailyMetrics(anyInt(), any(), any())).thenReturn(List.of());
                when(exhibitionService.aggregateDailyRevenueForExhibition(anyInt(), any(), any()))
                                .thenReturn(List.of());
                when(exhibitionService.countApprovedRegistrationsForExhibition(anyInt()))
                                .thenReturn(2L);
                when(analyticsEventRepository.averageVisitDurationSeconds(anyInt(), any(), any())).thenReturn(null);
                when(analyticsEventRepository.countUniqueVisitors(anyInt(), any(), any())).thenReturn(0L);
                when(exhibitionService.aggregatePaidPackageRevenueForExhibition(anyInt(), any(), any()))
                                .thenReturn(List.of());

                var dto = analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null);

                assertTrue(dto.isHasData());
                assertEquals(2, dto.getMetrics().getApprovedBoothCount());
                assertEquals(20.0, dto.getMetrics().getBoothFillRatePercent()); // 2/10
                assertEquals(0, dto.getMetrics().getTotalVisits());
                assertTrue(dto.getChart().isEmpty());
        }
}
