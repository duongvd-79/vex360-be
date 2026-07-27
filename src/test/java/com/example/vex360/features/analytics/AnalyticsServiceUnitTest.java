package com.example.vex360.features.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.analytics.services.AnalyticsService;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.ExhibitionStatus;
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
    ExhibitorRegistrationRepository exhibitorRegistrationRepository;
    @Mock
    PaymentRepository paymentRepository;

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
