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
import java.util.Optional;
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
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.chat.repositories.ChatMessageRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.lead.enums.LeadStatus;
import com.example.vex360.features.lead.repositories.BoothLeadRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceUnitTest {
    @Mock
    AnalyticsEventRepository analyticsEventRepository;
    @Mock
    ExhibitionRepository exhibitionRepository;
    @Mock
    BoothRepository boothRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    UserService userService;
    @Mock
    ChatRoomRepository chatRoomRepository;
    @Mock
    ChatMessageRepository chatMessageRepository;
    @Mock
    CompanyService companyService;
    @Mock
    ExhibitorRegistrationRepository exhibitorRegistrationRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    BoothLeadRepository boothLeadRepository;

    @InjectMocks
    AnalyticsService analyticsService; // Mockito tự tiêm các mock trên vào

    User organizer;
    Exhibition exhibition;

    @BeforeEach
    void setUp() {
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibition = Exhibition.builder()
                .id(1).uuid(UUID.randomUUID()).name("Demo")
                .organizer(organizer) // QUAN TRỌNG: cùng organizer để pass kiểm tra quyền
                .status(ExhibitionStatus.ACTIVE)
                .estimatedBooths(10) // mẫu số để tính tỷ lệ lấp đầy gian hàng
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now())
                .build();
    }

    @Test
    void getExhibitionAnalytics_notOwner_throwsUnauthorized() {
        User other = User.builder().id(UUID.randomUUID()).build(); // organizer KHÁC
        exhibition = Exhibition.builder().id(1).uuid(UUID.randomUUID())
                .organizer(other).build(); // triển lãm của người khác
        when(exhibitionRepository.findByUuid(any())).thenReturn(Optional.of(exhibition));

        AppException ex = assertThrows(AppException.class,
                () -> analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void getExhibitionAnalytics_withData_aggregatesCorrectly() {
        when(exhibitionRepository.findByUuid(any())).thenReturn(Optional.of(exhibition));
        when(boothRepository.countBoothsByExhibitionId(1)).thenReturn(3L);
        // Cột [1] (lượt xem gian hàng) cố ý bị bỏ qua: đó là số liệu của exhibitor,
        // organizer chỉ dùng cột [2] = lượt vào triển lãm và cột [3] = thời lượng TB.
        when(analyticsEventRepository.aggregateDailyMetrics(anyInt(), any(), any()))
                .thenReturn(List.of(
                        new Object[] { "2026-06-01", 10L, 4L, 600.0 },
                        new Object[] { "2026-06-02", 20L, 6L, 300.0 }));
        when(paymentRepository.aggregateDailyRevenue(anyInt(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] { "2026-06-01", BigDecimal.valueOf(5_000_000) },
                        new Object[] { "2026-06-02", BigDecimal.valueOf(3_000_000) }));
        when(exhibitorRegistrationRepository.countByExhibitionPackageExhibitionIdAndStatus(anyInt(), any()))
                .thenReturn(4L);
        // Thời lượng TB giờ lấy trực tiếp từ query (AVG toàn bộ lượt rời), tính bằng giây
        when(analyticsEventRepository.averageVisitDurationSeconds(anyInt(), any(), any()))
                .thenReturn(450.0); // 450 giây = 7.5 phút
        when(analyticsEventRepository.countUniqueVisitors(anyInt(), any(), any()))
                .thenReturn(7L);
        when(paymentRepository.aggregatePaidPackageRevenue(anyInt(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "Gói Cơ Bản", 2L, BigDecimal.valueOf(10_000_000) }));
        when(boothLeadRepository.countByStatusForExhibitionInRange(anyInt(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] { LeadStatus.NEW, 2L },
                        new Object[] { LeadStatus.CONVERTED, 2L }));
        when(boothLeadRepository.countUniqueLeadVisitorsForExhibition(anyInt(), any(), any())).thenReturn(3L);
        when(boothLeadRepository.countBoothsWithLeadsForExhibition(anyInt(), any(), any())).thenReturn(2L);
        when(boothLeadRepository.aggregateDailyForExhibition(anyInt(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-06-01", 4L }));
        when(boothLeadRepository.findTopBoothsForExhibition(
                anyInt(), any(), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[] { UUID.randomUUID(), "Mộc Việt", 4L }));

        var dto = analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null);

        assertTrue(dto.isHasData());
        assertEquals(10, dto.getMetrics().getTotalVisits()); // 4 + 6
        assertEquals(7, dto.getMetrics().getUniqueVisitorCount());
        assertEquals(7.5, dto.getMetrics().getAverageVisitDurationMinutes());
        assertEquals(8_000_000, dto.getMetrics().getTotalRevenue()); // 5tr + 3tr
        assertEquals(4, dto.getMetrics().getApprovedBoothCount());
        assertEquals(10, dto.getMetrics().getEstimatedBooths());
        assertEquals(40.0, dto.getMetrics().getBoothFillRatePercent()); // 4/10
        assertEquals(2, dto.getChart().size());
        assertEquals("2026-06-01", dto.getChart().get(0).getDate()); // TreeMap sort tăng dần
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
        when(exhibitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId()))
                .thenReturn(List.of(exhibition));
        when(boothRepository.countBoothsGroupedByExhibition(List.of(1)))
                .thenReturn(List.<Object[]>of(new Object[] { 1, 4L }));
        when(exhibitorRegistrationRepository.countByStatusGroupedByExhibition(
                List.of(1), ExhibitorRegistrationStatus.APPROVED))
                .thenReturn(List.<Object[]>of(new Object[] { 1, 4L }));
        when(exhibitorRegistrationRepository.countByStatusGroupedByExhibition(
                List.of(1), ExhibitorRegistrationStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[] { 1, 2L }));
        when(analyticsEventRepository.aggregatePerformanceByExhibition(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 1, 20L, 7L, 300.0 }));
        when(paymentRepository.aggregateRevenueByExhibition(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 1, BigDecimal.valueOf(12_000_000) }));
        when(boothLeadRepository.aggregatePerformanceForExhibitions(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 1, 5L, 3L, 2L }));
        when(analyticsEventRepository.aggregateOrganizerDailyMetrics(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 20L, 7L, 300.0 }));
        when(paymentRepository.aggregateOrganizerDailyRevenue(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", BigDecimal.valueOf(12_000_000) }));
        when(boothLeadRepository.aggregateDailyForExhibitions(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 5L }));
        when(exhibitorRegistrationRepository.aggregateDailySubmissions(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-07-01", 2L }));
        when(paymentRepository.aggregateOrganizerPackageRevenue(any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] { "Gói Tiêu chuẩn", 2L, BigDecimal.valueOf(12_000_000) }));
        when(analyticsEventRepository.countUniqueVisitorsForExhibitions(any(), any(), any())).thenReturn(7L);
        when(boothLeadRepository.countUniqueLeadVisitorsForExhibitions(any(), any(), any())).thenReturn(3L);
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

        when(boothRepository.findById(boothId)).thenReturn(Optional.of(booth));
        when(analyticsEventRepository.aggregateBoothDailyMetrics(
                org.mockito.ArgumentMatchers.eq(boothId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(boothLeadRepository.countByStatusForBoothInRange(
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
        when(boothRepository.findCompanyBooths(
                org.mockito.ArgumentMatchers.eq(company.getId()), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(boothLeadRepository.countByStatusForCompanyInRange(
                org.mockito.ArgumentMatchers.eq(company.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[] { LeadStatus.NEW, 3L },
                        new Object[] { LeadStatus.CONVERTED, 1L }));
        when(chatMessageRepository.countUnreadForExhibitor(exhibitor.getId())).thenReturn(0L);

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
        // Triển lãm đã bán được gian hàng nhưng chưa có khách nào vào: organizer vẫn
        // cần thấy tỷ lệ lấp đầy thay vì màn hình trống "chưa có dữ liệu".
        when(exhibitionRepository.findByUuid(any())).thenReturn(Optional.of(exhibition));
        when(boothRepository.countBoothsByExhibitionId(1)).thenReturn(2L);
        when(analyticsEventRepository.aggregateDailyMetrics(anyInt(), any(), any())).thenReturn(List.of());
        when(paymentRepository.aggregateDailyRevenue(anyInt(), any(), any())).thenReturn(List.of());
        when(exhibitorRegistrationRepository.countByExhibitionPackageExhibitionIdAndStatus(anyInt(), any()))
                .thenReturn(2L);
        when(analyticsEventRepository.averageVisitDurationSeconds(anyInt(), any(), any())).thenReturn(null);
        when(analyticsEventRepository.countUniqueVisitors(anyInt(), any(), any())).thenReturn(0L);
        when(paymentRepository.aggregatePaidPackageRevenue(anyInt(), any(), any())).thenReturn(List.of());

        var dto = analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null);

        assertTrue(dto.isHasData());
        assertEquals(2, dto.getMetrics().getApprovedBoothCount());
        assertEquals(20.0, dto.getMetrics().getBoothFillRatePercent()); // 2/10
        assertEquals(0, dto.getMetrics().getTotalVisits());
        assertTrue(dto.getChart().isEmpty());
    }
}
