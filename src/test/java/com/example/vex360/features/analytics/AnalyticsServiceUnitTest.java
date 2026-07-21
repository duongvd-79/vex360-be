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

    @InjectMocks
    AnalyticsService analyticsService; // Mockito tự tiêm 7 mock trên vào

    User organizer;
    Exhibition exhibition;

    @BeforeEach
    void setUp() {
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibition = Exhibition.builder()
                .id(1).uuid(UUID.randomUUID()).name("Demo")
                .organizer(organizer) // QUAN TRỌNG: cùng organizer để pass kiểm tra quyền
                .status(ExhibitionStatus.ACTIVE)
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
        when(analyticsEventRepository.aggregateDailyMetrics(anyInt(), any(), any()))
                .thenReturn(List.of(
                        new Object[] { "2026-06-01", 10L, 4L, 600.0 },
                        new Object[] { "2026-06-02", 20L, 6L, 300.0 }));
        when(chatRoomRepository.countDailyChats(anyInt(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "2026-06-01", 3L }));
        // Thời lượng TB giờ lấy trực tiếp từ query (AVG toàn bộ lượt rời), tính bằng giây
        when(analyticsEventRepository.averageVisitDurationSeconds(anyInt(), any(), any()))
                .thenReturn(450.0); // 450 giây = 7.5 phút
        when(exhibitorRegistrationRepository.aggregatePackageSales(anyInt(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { "Gói Cơ Bản", 2L, BigDecimal.valueOf(10_000_000) }));

        var dto = analyticsService.getExhibitionAnalytics(organizer, exhibition.getUuid(), null, null);

        assertTrue(dto.isHasData());
        assertEquals(30, dto.getMetrics().getTotalViews()); // 10 + 20
        assertEquals(10, dto.getMetrics().getTotalVisits()); // 4 + 6
        assertEquals(3, dto.getMetrics().getTotalChats());
        assertEquals(7.5, dto.getMetrics().getAverageVisitDurationMinutes()); // (600/60 + 300/60)/2
        assertEquals(2, dto.getChart().size());
        assertEquals("2026-06-01", dto.getChart().get(0).getDate()); // TreeMap sort tăng dần
        assertEquals(1, dto.getPackages().size());
        assertEquals(10_000_000, dto.getPackages().get(0).getRevenue());
    }
}
