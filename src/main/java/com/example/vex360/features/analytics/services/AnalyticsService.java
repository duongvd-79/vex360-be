package com.example.vex360.features.analytics.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.analytics.dtos.request.RecordAnalyticsEventRequest;
import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsDetailDTO;
import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsOverviewDTO;
import com.example.vex360.features.analytics.entities.AnalyticsEvent;
import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final BoothRepository boothRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final ChatRoomRepository chatRoomRepository;
    private final ExhibitorRegistrationRepository exhibitorRegistrationRepository;

    @Transactional
    public void recordEvent(User currentUser, RecordAnalyticsEventRequest request) {
        User user = userService.getUserEntityById(currentUser.getId());

        // 1. Tra cứu các entity liên quan nếu client có gửi ID
        Exhibition exhibition = null;
        if (request.getExhibitionUuid() != null && !request.getExhibitionUuid().isBlank()) {
            exhibition = exhibitionRepository.findByUuid(UUID.fromString(request.getExhibitionUuid()))
                    .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));
        }

        Booth booth = null;
        if (request.getBoothId() != null) {
            booth = boothRepository.findById(request.getBoothId())
                    .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        }

        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        }

        // 2. Nếu event gắn với booth mà chưa biết exhibition -> suy ra từ booth
        if (exhibition == null && booth != null && booth.getExhibitorRegistration() != null) {
            exhibition = booth.getExhibitorRegistration().getExhibitionPackage().getExhibition();
        }

        // 3. Lưu event
        AnalyticsEvent event = AnalyticsEvent.builder()
                .user(user)
                .exhibition(exhibition)
                .booth(booth)
                .product(product)
                .eventType(request.getEventType())
                .durationSeconds(request.getDurationSeconds())
                .metadataJson(request.getMetadataJson())
                .build();

        analyticsEventRepository.save(event);
    }

    // ================= Tổng quan (overview) =================

    @Transactional(readOnly = true)
    public List<ExhibitionAnalyticsOverviewDTO> getOrganizerOverview(User organizer) {
        List<Exhibition> exhibitions = exhibitionRepository
                .findByOrganizerIdOrderByCreatedAtDesc(organizer.getId());
        if (exhibitions.isEmpty()) {
            return List.of();
        }

        // Đếm booth GOM 1 lần cho tất cả triển lãm -> tránh N+1 query
        List<Integer> ids = exhibitions.stream().map(Exhibition::getId).toList();
        Map<Integer, Long> boothCountById = new HashMap<>();
        for (Object[] row : boothRepository.countBoothsGroupedByExhibition(ids)) {
            boothCountById.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        return exhibitions.stream()
                .map(exhibition -> toOverviewDTO(exhibition,
                        boothCountById.getOrDefault(exhibition.getId(), 0L)))
                .toList();
    }

    /** Biến 1 Exhibition thành 1 OverviewDTO (nhận sẵn số booth để tránh query lặp). */
    private ExhibitionAnalyticsOverviewDTO toOverviewDTO(Exhibition exhibition, long boothCount) {
        return ExhibitionAnalyticsOverviewDTO.builder()
                .id(exhibition.getUuid().toString())
                .code("EXH-" + String.format("%03d", exhibition.getId()))
                .name(exhibition.getName())
                .status(statusLabel(exhibition.getStatus()))
                .location("Trực tuyến trên nền tảng VEX360")
                .startDate(exhibition.getStartDate())
                .endDate(exhibition.getEndDate())
                .boothCount(boothCount)
                .build();
    }

    private String statusLabel(ExhibitionStatus status) {
        return switch (status) {
            case PENDING -> "Chờ duyệt";
            case ACTIVE -> "Đang diễn ra";
            case REJECTED -> "Bị từ chối";
            case REGISTRATION -> "Đang đăng ký";
            case PUBLISHED -> "Đã công bố";
            case COMPLETED -> "Đã kết thúc";
        };
    }

    // ================= Chi tiết (detail) =================

    @Transactional(readOnly = true)
    public ExhibitionAnalyticsDetailDTO getExhibitionAnalytics(User organizer, UUID exhibitionUuid,
            LocalDate startDate, LocalDate endDate) {
        // Chặng 1: tìm triển lãm + kiểm tra quyền sở hữu
        Exhibition exhibition = exhibitionRepository.findByUuid(exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));
        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Chặng 2: xác định khoảng ngày (mặc định theo lịch triển lãm nếu client không gửi)
        LocalDate rangeStart = startDate != null ? startDate : exhibition.getStartDate();
        LocalDate rangeEnd = endDate != null ? endDate : exhibition.getEndDate();
        Instant startDateTime = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endDateTime = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        // Chặng 3: gọi query lấy dữ liệu thô
        Integer exhibitionId = exhibition.getId();
        long boothCount = boothRepository.countBoothsByExhibitionId(exhibitionId);
        List<Object[]> dailyRows = analyticsEventRepository.aggregateDailyMetrics(exhibitionId, startDateTime,
                endDateTime);
        List<Object[]> chatRows = chatRoomRepository.countDailyChats(exhibitionId, startDateTime, endDateTime);
        List<Object[]> packageRows = exhibitorRegistrationRepository.aggregatePackageSales(exhibitionId,
                ExhibitorRegistrationStatus.APPROVED);

        // Chặng 4: gộp view/visit/thời lượng + chat theo từng ngày -> chart (TreeMap tự sort theo ngày)
        Map<String, ExhibitionAnalyticsDetailDTO.ChartPoint> chartByDate = new TreeMap<>();

        for (Object[] row : dailyRows) {
            String dateKey = row[0].toString();
            long views = ((Number) row[1]).longValue();
            long visits = ((Number) row[2]).longValue();
            double avgSeconds = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
            chartByDate.put(dateKey, ExhibitionAnalyticsDetailDTO.ChartPoint.builder()
                    .date(dateKey)
                    .views(views)
                    .visits(visits)
                    .chats(0)
                    .averageVisitDurationMinutes(avgSeconds / 60.0) // giây -> phút
                    .build());
        }

        for (Object[] row : chatRows) {
            String dateKey = row[0].toString();
            long chats = ((Number) row[1]).longValue();
            ExhibitionAnalyticsDetailDTO.ChartPoint point = chartByDate.get(dateKey);
            if (point != null) {
                point.setChats(chats);
            } else {
                chartByDate.put(dateKey, ExhibitionAnalyticsDetailDTO.ChartPoint.builder()
                        .date(dateKey)
                        .views(0)
                        .visits(0)
                        .chats(chats)
                        .averageVisitDurationMinutes(0.0)
                        .build());
            }
        }

        List<ExhibitionAnalyticsDetailDTO.ChartPoint> chart = new ArrayList<>(chartByDate.values());

        // Không có dữ liệu trong kỳ -> trả về hasData=false (frontend hiện "Không có dữ liệu")
        if (chart.isEmpty()) {
            return ExhibitionAnalyticsDetailDTO.builder()
                    .exhibition(toOverviewDTO(exhibition, boothCount))
                    .startDate(rangeStart.toString())
                    .endDate(rangeEnd.toString())
                    .hasData(false)
                    .messageCode("Chưa có dữ liệu thống kê.")
                    .message("Không có dữ liệu thống kê cho triển lãm đã chọn trong khoảng thời gian này.")
                    .metrics(null)
                    .chart(List.of())
                    .packages(List.of())
                    .build();
        }

        // Chặng 5: cộng dồn từ chart -> số tổng view/visit/chat
        long totalViews = 0;
        long totalVisits = 0;
        long totalChats = 0;
        for (ExhibitionAnalyticsDetailDTO.ChartPoint p : chart) {
            totalViews += p.getViews();
            totalVisits += p.getVisits();
            totalChats += p.getChats();
        }
        // Thời lượng visit TB = AVG trên TOÀN BỘ lượt rời (trung bình có trọng số,
        // không phải trung-bình-của-trung-bình theo ngày)
        Double avgDurationSeconds = analyticsEventRepository.averageVisitDurationSeconds(
                exhibitionId, startDateTime, endDateTime);
        double averageVisitDurationMinutes = avgDurationSeconds == null ? 0.0 : avgDurationSeconds / 60.0;

        ExhibitionAnalyticsDetailDTO.Metrics metrics = ExhibitionAnalyticsDetailDTO.Metrics.builder()
                .totalViews(totalViews)
                .totalVisits(totalVisits)
                .totalChats(totalChats)
                .averageVisitDurationMinutes(averageVisitDurationMinutes)
                .build();

        // Chặng 6: doanh số theo gói
        List<ExhibitionAnalyticsDetailDTO.PackageSummary> packages = new ArrayList<>();
        for (Object[] row : packageRows) {
            String packageName = row[0] != null ? row[0].toString() : "Không xác định";
            long quantity = ((Number) row[1]).longValue();
            long revenue = row[2] == null ? 0L : ((Number) row[2]).longValue();
            packages.add(ExhibitionAnalyticsDetailDTO.PackageSummary.builder()
                    .key(packageName)
                    .label(packageName)
                    .quantity(quantity)
                    .revenue(revenue)
                    .build());
        }

        // Chặng 7: ráp DTO đầy đủ
        return ExhibitionAnalyticsDetailDTO.builder()
                .exhibition(toOverviewDTO(exhibition, boothCount))
                .startDate(rangeStart.toString())
                .endDate(rangeEnd.toString())
                .hasData(true)
                .messageCode(null)
                .message("")
                .metrics(metrics)
                .chart(chart)
                .packages(packages)
                .build();
    }
}
