package com.example.vex360.features.analytics.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.vex360.features.analytics.dtos.request.RecordAnalyticsEventRequest;
import com.example.vex360.features.analytics.dtos.response.BoothAnalyticsDetailDTO;
import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsDetailDTO;
import com.example.vex360.features.analytics.dtos.response.ExhibitionAnalyticsOverviewDTO;
import com.example.vex360.features.analytics.dtos.response.ExhibitorDashboardOverviewDTO;
import com.example.vex360.features.analytics.dtos.response.OrganizerBoothRankingItemDTO;
import com.example.vex360.features.analytics.dtos.response.OrganizerBoothRankingPageDTO;
import com.example.vex360.features.analytics.entities.AnalyticsEvent;
import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsEventRepository analyticsEventRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CompanyService companyService;
    private final ExhibitionRepository exhibitionRepository;
    private final BoothRepository boothRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final ChatRoomRepository chatRoomRepository;
    private final ExhibitorRegistrationRepository exhibitorRegistrationRepository;
    private final PaymentRepository paymentRepository;

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

        // Đếm booth + lượt vào triển lãm GOM 1 lần cho tất cả triển lãm -> tránh N+1 query
        List<Integer> ids = exhibitions.stream().map(Exhibition::getId).toList();
        Map<Integer, Long> boothCountById = new HashMap<>();
        for (Object[] row : boothRepository.countBoothsGroupedByExhibition(ids)) {
            boothCountById.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        Map<Integer, Long> visitCountById = new HashMap<>();
        for (Object[] row : analyticsEventRepository.countVisitsGroupedByExhibition(ids)) {
            visitCountById.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        return exhibitions.stream()
                .map(exhibition -> toOverviewDTO(exhibition,
                        boothCountById.getOrDefault(exhibition.getId(), 0L),
                        visitCountById.getOrDefault(exhibition.getId(), 0L)))
                .toList();
    }

    /** Biến 1 Exhibition thành 1 OverviewDTO (nhận sẵn số booth để tránh query lặp). */
    private ExhibitionAnalyticsOverviewDTO toOverviewDTO(Exhibition exhibition, long boothCount) {
        return toOverviewDTO(exhibition, boothCount, 0L);
    }

    /** Bản đầy đủ, có kèm tổng lượt vào triển lãm để xếp hạng ở màn tổng quan. */
    private ExhibitionAnalyticsOverviewDTO toOverviewDTO(Exhibition exhibition, long boothCount, long totalVisits) {
        return ExhibitionAnalyticsOverviewDTO.builder()
                .id(exhibition.getUuid().toString())
                .code("EXH-" + String.format("%03d", exhibition.getId()))
                .name(exhibition.getName())
                .status(statusLabel(exhibition.getStatus()))
                .location("Trực tuyến trên nền tảng VEX360")
                .startDate(exhibition.getStartDate())
                .endDate(exhibition.getEndDate())
                .boothCount(boothCount)
                .estimatedBooths(exhibition.getEstimatedBooths())
                .totalVisits(totalVisits)
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
        List<Object[]> revenueRows = paymentRepository.aggregateDailyRevenue(exhibitionId, startDateTime,
                endDateTime);
        List<Object[]> packageRows = paymentRepository.aggregatePaidPackageRevenue(exhibitionId,
                startDateTime, endDateTime);
        List<Object[]> visitorsByHourRows = analyticsEventRepository.aggregateExhibitionVisitorsByHour(
                exhibitionId, startDateTime, endDateTime);

        // Mức độ lấp đầy gian hàng: tính riêng, KHÔNG phụ thuộc khoảng ngày lọc, vì
        // đây là trạng thái hiện tại của triển lãm chứ không phải số liệu theo thời gian.
        long approvedBoothCount = exhibitorRegistrationRepository
                .countByExhibitionPackageExhibitionIdAndStatus(exhibitionId, ExhibitorRegistrationStatus.APPROVED);
        Integer estimatedBooths = exhibition.getEstimatedBooths();
        double boothFillRatePercent = (estimatedBooths == null || estimatedBooths <= 0)
                ? 0.0
                : (approvedBoothCount * 100.0) / estimatedBooths;

        // Chặng 4: gộp lượt vào triển lãm + doanh thu theo từng ngày -> chart
        // (TreeMap tự sort theo ngày). Cố ý KHÔNG lấy lượt xem/chat từng gian hàng —
        // đó là thống kê của exhibitor, organizer nhìn ở tầng triển lãm.
        Map<String, ExhibitionAnalyticsDetailDTO.ChartPoint> chartByDate = new TreeMap<>();

        for (Object[] row : dailyRows) {
            String dateKey = row[0].toString();
            long visits = ((Number) row[2]).longValue();
            double avgSeconds = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
            chartByDate.put(dateKey, ExhibitionAnalyticsDetailDTO.ChartPoint.builder()
                    .date(dateKey)
                    .visits(visits)
                    .revenue(0)
                    .averageVisitDurationMinutes(avgSeconds / 60.0) // giây -> phút
                    .build());
        }

        for (Object[] row : revenueRows) {
            String dateKey = row[0].toString();
            long revenue = row[1] == null ? 0L : ((Number) row[1]).longValue();
            ExhibitionAnalyticsDetailDTO.ChartPoint point = chartByDate.get(dateKey);
            if (point != null) {
                point.setRevenue(revenue);
            } else {
                chartByDate.put(dateKey, ExhibitionAnalyticsDetailDTO.ChartPoint.builder()
                        .date(dateKey)
                        .visits(0)
                        .revenue(revenue)
                        .averageVisitDurationMinutes(0.0)
                        .build());
            }
        }

        List<ExhibitionAnalyticsDetailDTO.ChartPoint> chart = new ArrayList<>(chartByDate.values());

        // Chỉ coi là "chưa có dữ liệu" khi vừa không có lượt truy cập/doanh thu nào,
        // vừa chưa duyệt gian hàng nào — nếu đã có gian hàng được duyệt thì organizer
        // vẫn cần thấy tỷ lệ lấp đầy dù chưa có khách vào.
        if (chart.isEmpty() && approvedBoothCount == 0) {
            return ExhibitionAnalyticsDetailDTO.builder()
                    .exhibition(toOverviewDTO(exhibition, boothCount))
                    .startDate(rangeStart.toString())
                    .endDate(rangeEnd.toString())
                    .hasData(false)
                    .messageCode("Chưa có dữ liệu thống kê.")
                    .message("Không có dữ liệu thống kê cho triển lãm đã chọn trong khoảng thời gian này.")
                    .metrics(null)
                    .chart(List.of())
                    .visitorsByHour(List.of())
                    .packages(List.of())
                    .build();
        }

        // Chặng 5: cộng dồn từ chart -> tổng lượt vào triển lãm + tổng doanh thu trong kỳ
        long totalVisits = 0;
        long totalRevenue = 0;
        for (ExhibitionAnalyticsDetailDTO.ChartPoint p : chart) {
            totalVisits += p.getVisits();
            totalRevenue += p.getRevenue();
        }
        // Thời lượng visit TB = AVG trên TOÀN BỘ lượt rời (trung bình có trọng số,
        // không phải trung-bình-của-trung-bình theo ngày)
        Double avgDurationSeconds = analyticsEventRepository.averageVisitDurationSeconds(
                exhibitionId, startDateTime, endDateTime);
        double averageVisitDurationMinutes = avgDurationSeconds == null ? 0.0 : avgDurationSeconds / 60.0;
        long uniqueVisitorCount = analyticsEventRepository.countUniqueVisitors(
                exhibitionId, startDateTime, endDateTime);

        ExhibitionAnalyticsDetailDTO.Metrics metrics = ExhibitionAnalyticsDetailDTO.Metrics.builder()
                .approvedBoothCount(approvedBoothCount)
                .estimatedBooths(estimatedBooths)
                .boothFillRatePercent(boothFillRatePercent)
                .totalRevenue(totalRevenue)
                .totalVisits(totalVisits)
                .uniqueVisitorCount(uniqueVisitorCount)
                .averageVisitDurationMinutes(averageVisitDurationMinutes)
                .build();
        long[] visitorsByHour = new long[24];
        for (Object[] row : visitorsByHourRows) {
            int hour = ((Number) row[0]).intValue();
            if (hour >= 0 && hour < visitorsByHour.length) {
                visitorsByHour[hour] = ((Number) row[1]).longValue();
            }
        }
        List<ExhibitionAnalyticsDetailDTO.HourPoint> hourPoints = new ArrayList<>();
        for (int hour = 0; hour < visitorsByHour.length; hour++) {
            hourPoints.add(ExhibitionAnalyticsDetailDTO.HourPoint.builder()
                    .hour(hour).visitors(visitorsByHour[hour]).build());
        }

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
                .visitorsByHour(hourPoints)
                .packages(packages)
                .build();
    }

    /**
     * Bảng xếp hạng gian hàng của một triển lãm do organizer quản lý, sắp xếp theo
     * lượt xem giảm dần để thấy ngay gian hàng nào đang thu hút nhất.
     */
    @Transactional(readOnly = true)
    public List<OrganizerBoothRankingItemDTO> getOrganizerBoothRanking(User organizer, UUID exhibitionUuid,
            LocalDate startDate, LocalDate endDate) {
        return getOrganizerBoothRankingPage(organizer, exhibitionUuid, startDate, endDate, "", "viewCount", "desc", "all", 0,
                Integer.MAX_VALUE).getItems();
    }

    @Transactional(readOnly = true)
    public OrganizerBoothRankingPageDTO getOrganizerBoothRankingPage(User organizer, UUID exhibitionUuid,
            LocalDate startDate, LocalDate endDate, String keyword, String sortBy, String sortDirection,
            String interaction, int page, int size) {
        Exhibition exhibition = exhibitionRepository.findByUuid(exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));
        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Mặc định 30 ngày gần nhất nếu client không gửi khoảng ngày
        LocalDate rangeEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate rangeStart = startDate != null ? startDate : rangeEnd.minusDays(29);
        Instant startDateTime = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endDateTime = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        List<Object[]> rows = analyticsEventRepository.aggregateBoothRankingForOrganizer(
                List.of(exhibition.getId()),
                AnalyticsEventType.BOOTH_VIEW,
                AnalyticsEventType.CHAT_INITIATED,
                AnalyticsEventType.BOOTH_LEAVE,
                startDateTime,
                endDateTime);

        Map<UUID, OrganizerBoothRankingItemDTO> itemsByBooth = new LinkedHashMap<>();
        for (Object[] row : rows) {
            double avgSeconds = row[7] == null ? 0.0 : ((Number) row[7]).doubleValue();
            OrganizerBoothRankingItemDTO item = OrganizerBoothRankingItemDTO.builder()
                    .boothId((UUID) row[0])
                    .boothName((String) row[1])
                    .exhibitionUuid(row[2] == null ? null : row[2].toString())
                    .exhibitionName((String) row[3])
                    .visitorCount(((Number) row[4]).longValue())
                    .viewCount(((Number) row[5]).longValue())
                    .chatCount(((Number) row[6]).longValue())
                    .averageTimeInBoothMinutes(avgSeconds / 60.0) // giây -> phút
                    .build();
            itemsByBooth.put(item.getBoothId(), item);
        }
        for (Booth booth : boothRepository.findBoothsByExhibitionId(exhibition.getId())) {
            itemsByBooth.putIfAbsent(booth.getId(), OrganizerBoothRankingItemDTO.builder()
                    .boothId(booth.getId()).boothName(booth.getName())
                    .exhibitionUuid(exhibition.getUuid().toString()).exhibitionName(exhibition.getName())
                    .visitorCount(0).viewCount(0).chatCount(0).averageTimeInBoothMinutes(0).build());
        }
        List<OrganizerBoothRankingItemDTO> items = new ArrayList<>(itemsByBooth.values());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKeyword.isEmpty()) {
            items.removeIf(item -> item.getBoothName() == null
                    || !item.getBoothName().toLowerCase(Locale.ROOT).contains(normalizedKeyword));
        }
        if ("withInteraction".equalsIgnoreCase(interaction) || "withoutInteraction".equalsIgnoreCase(interaction)) {
            boolean withInteraction = "withInteraction".equalsIgnoreCase(interaction);
            items.removeIf(item -> {
                boolean hasInteraction = item.getViewCount() > 0 || item.getVisitorCount() > 0
                        || item.getChatCount() > 0 || item.getAverageTimeInBoothMinutes() > 0;
                return withInteraction != hasInteraction;
            });
        }

        Comparator<OrganizerBoothRankingItemDTO> comparator = switch (sortBy == null ? "viewCount" : sortBy) {
            case "visitorCount" -> Comparator.comparingLong(OrganizerBoothRankingItemDTO::getVisitorCount);
            case "chatCount" -> Comparator.comparingLong(OrganizerBoothRankingItemDTO::getChatCount);
            case "averageTimeInBoothMinutes" -> Comparator
                    .comparingDouble(OrganizerBoothRankingItemDTO::getAverageTimeInBoothMinutes);
            default -> Comparator.comparingLong(OrganizerBoothRankingItemDTO::getViewCount);
        };
        if (!"asc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(item -> item.getBoothName() == null ? "" : item.getBoothName(),
                String.CASE_INSENSITIVE_ORDER);
        items.sort(comparator);

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int fromIndex = Math.min(safePage * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        return OrganizerBoothRankingPageDTO.builder()
                .items(items.subList(fromIndex, toIndex))
                .total(items.size())
                .page(safePage)
                .size(safeSize)
                .build();
    }

    // ================= Thống kê gian hàng (booth) cho exhibitor =================

    @Transactional(readOnly = true)
    public BoothAnalyticsDetailDTO getBoothAnalytics(User exhibitor, UUID boothId,
            LocalDate startDate, LocalDate endDate) {
        // Chặng 1: tìm booth + kiểm tra quyền sở hữu (booth phải do chính exhibitor tạo)
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        if (booth.getCreatedBy() == null || !booth.getCreatedBy().getId().equals(exhibitor.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Chặng 2: khoảng ngày mặc định = 30 ngày gần nhất nếu client không gửi
        LocalDate rangeEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate rangeStart = startDate != null ? startDate : rangeEnd.minusDays(29);
        Instant startDateTime = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endDateTime = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        BoothAnalyticsDetailDTO.BoothSummary summary = toBoothSummary(booth);

        // Chặng 3: query dữ liệu thô theo ngày
        List<Object[]> dailyRows = analyticsEventRepository.aggregateBoothDailyMetrics(
                boothId, startDateTime, endDateTime);

        // Không có tương tác trong kỳ -> hasData=false (frontend hiện "Không có dữ liệu")
        if (dailyRows.isEmpty()) {
            return BoothAnalyticsDetailDTO.builder()
                    .booth(summary)
                    .startDate(rangeStart.toString())
                    .endDate(rangeEnd.toString())
                    .hasData(false)
                    .messageCode("Chưa có dữ liệu thống kê.")
                    .message("Gian hàng chưa có lượt tương tác nào trong khoảng thời gian này.")
                    .metrics(null)
                    .chart(List.of())
                    .topClickables(List.of())
                    .durationHistogram(List.of())
                    .viewsByHour(List.of())
                    .build();
        }

        // Chặng 4: dựng chart theo ngày + cộng dồn tổng
        List<BoothAnalyticsDetailDTO.ChartPoint> chart = new ArrayList<>();
        long totalViews = 0;
        long totalProductClicks = 0;
        long totalChats = 0;
        for (Object[] row : dailyRows) {
            String dateKey = row[0].toString();
            long views = ((Number) row[1]).longValue();
            long productClicks = ((Number) row[2]).longValue();
            long chats = ((Number) row[3]).longValue();
            double avgSeconds = row[4] == null ? 0.0 : ((Number) row[4]).doubleValue();
            chart.add(BoothAnalyticsDetailDTO.ChartPoint.builder()
                    .date(dateKey)
                    .views(views)
                    .productClicks(productClicks)
                    .chats(chats)
                    .averageTimeInBoothMinutes(avgSeconds / 60.0) // giây -> phút
                    .build());
            totalViews += views;
            totalProductClicks += productClicks;
            totalChats += chats;
        }

        // Thời lượng ở booth TB = AVG trên toàn bộ lượt rời (trung bình có trọng số,
        // không phải trung-bình-của-trung-bình theo ngày)
        Double avgDurationSeconds = analyticsEventRepository.averageBoothDurationSeconds(
                boothId, startDateTime, endDateTime);
        double averageTimeInBoothMinutes = avgDurationSeconds == null ? 0.0 : avgDurationSeconds / 60.0;

        BoothAnalyticsDetailDTO.Metrics metrics = BoothAnalyticsDetailDTO.Metrics.builder()
                .totalViews(totalViews)
                .totalProductClicks(totalProductClicks)
                .totalChats(totalChats)
                .averageTimeInBoothMinutes(averageTimeInBoothMinutes)
                .build();

        // Chặng 5: xếp hạng clickable, histogram thời lượng, view theo giờ
        List<BoothAnalyticsDetailDTO.ClickableRank> topClickables = computeTopClickables(
                analyticsEventRepository.findBoothClickMetadata(boothId, startDateTime, endDateTime));
        List<BoothAnalyticsDetailDTO.DurationBucket> durationHistogram = computeDurationHistogram(
                analyticsEventRepository.findBoothLeaveDurations(boothId, startDateTime, endDateTime));
        List<BoothAnalyticsDetailDTO.HourPoint> viewsByHour = computeViewsByHour(
                analyticsEventRepository.aggregateBoothViewsByHour(boothId, startDateTime, endDateTime));

        return BoothAnalyticsDetailDTO.builder()
                .booth(summary)
                .startDate(rangeStart.toString())
                .endDate(rangeEnd.toString())
                .hasData(true)
                .messageCode(null)
                .message("")
                .metrics(metrics)
                .chart(chart)
                .topClickables(topClickables)
                .durationHistogram(durationHistogram)
                .viewsByHour(viewsByHour)
                .build();
    }

    /** Gom các lượt click theo tên clickable (đọc từ metadata_json), nhiều nhất trước, tối đa 10. */
    private List<BoothAnalyticsDetailDTO.ClickableRank> computeTopClickables(List<String> metadataList) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String meta : metadataList) {
            String name = extractClickableName(meta);
            counts.merge(name, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(entry -> BoothAnalyticsDetailDTO.ClickableRank.builder()
                        .name(entry.getKey())
                        .clicks(entry.getValue())
                        .build())
                .toList();
    }

    private String extractClickableName(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "Không xác định";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(metadataJson);
            String name = node.path("name").asText(null);
            return (name == null || name.isBlank()) ? "Không xác định" : name;
        } catch (Exception e) {
            return "Không xác định";
        }
    }

    /** Chia thời lượng từng lượt rời booth (giây) vào 5 khoảng cố định. */
    private List<BoothAnalyticsDetailDTO.DurationBucket> computeDurationHistogram(List<Integer> durations) {
        long under30 = 0;
        long from30to60 = 0;
        long from1to3 = 0;
        long from3to5 = 0;
        long over5 = 0;
        for (Integer d : durations) {
            if (d == null) {
                continue;
            }
            int s = d;
            if (s < 30) {
                under30++;
            } else if (s < 60) {
                from30to60++;
            } else if (s < 180) {
                from1to3++;
            } else if (s < 300) {
                from3to5++;
            } else {
                over5++;
            }
        }
        List<BoothAnalyticsDetailDTO.DurationBucket> buckets = new ArrayList<>();
        buckets.add(durationBucket("Dưới 30 giây", under30));
        buckets.add(durationBucket("30 - 60 giây", from30to60));
        buckets.add(durationBucket("1 - 3 phút", from1to3));
        buckets.add(durationBucket("3 - 5 phút", from3to5));
        buckets.add(durationBucket("Trên 5 phút", over5));
        return buckets;
    }

    private BoothAnalyticsDetailDTO.DurationBucket durationBucket(String label, long count) {
        return BoothAnalyticsDetailDTO.DurationBucket.builder().label(label).count(count).build();
    }

    /** Điền đủ 24 giờ (0 nếu thiếu) để biểu đồ theo giờ luôn liền mạch. */
    private List<BoothAnalyticsDetailDTO.HourPoint> computeViewsByHour(List<Object[]> rows) {
        long[] byHour = new long[24];
        for (Object[] row : rows) {
            int hour = ((Number) row[0]).intValue();
            long views = ((Number) row[1]).longValue();
            if (hour >= 0 && hour < 24) {
                byHour[hour] = views;
            }
        }
        List<BoothAnalyticsDetailDTO.HourPoint> points = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            points.add(BoothAnalyticsDetailDTO.HourPoint.builder().hour(h).views(byHour[h]).build());
        }
        return points;
    }

    private BoothAnalyticsDetailDTO.BoothSummary toBoothSummary(Booth booth) {
        return BoothAnalyticsDetailDTO.BoothSummary.builder()
                .id(booth.getId().toString())
                .name(booth.getName())
                .status(boothStatusLabel(booth.getStatus()))
                .build();
    }

    private String boothStatusLabel(BoothStatus status) {
        return switch (status) {
            case DRAFT -> "Đang chỉnh sửa";
            case DESIGN_REQUEST_PENDING -> "Chờ phân công thiết kế";
            case DESIGNING -> "Đang được thiết kế";
            case PENDING -> "Chờ duyệt nội dung";
            case PUBLISHED -> "Đã xuất bản";
            case ARCHIVED -> "Đã lưu trữ";
        };
    }

    // ================= Bảng điều khiển exhibitor (gộp mọi gian hàng) =================

    /** Số booth điểm view giữ tạm khi xếp hạng top gian hàng nổi bật. */
    private record BoothViewCount(Booth booth, long views) {
    }

    @Transactional(readOnly = true)
    public ExhibitorDashboardOverviewDTO getExhibitorDashboardOverview(User exhibitor,
            LocalDate startDate, LocalDate endDate) {
        // Chặng 1: tìm công ty + toàn bộ gian hàng (không phân trang) thuộc exhibitor
        Company company = companyService.getCompanyEntityForCurrentUser(exhibitor);
        List<Booth> booths = boothRepository.findCompanyBooths(company.getId(), Pageable.unpaged()).getContent();

        // Chặng 2: khoảng ngày mặc định = 30 ngày gần nhất nếu client không gửi
        LocalDate rangeEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate rangeStart = startDate != null ? startDate : rangeEnd.minusDays(29);
        Instant startDateTime = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endDateTime = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        // Chặng 3: chart tổng hợp theo ngày (view + tương tác) gộp mọi gian hàng
        List<ExhibitorDashboardOverviewDTO.ChartPoint> chart = new ArrayList<>();
        long totalViews = 0;
        long totalInteractions = 0;
        List<UUID> boothIds = booths.stream().map(Booth::getId).toList();
        if (!boothIds.isEmpty()) {
            List<Object[]> rows = analyticsEventRepository.aggregateCompanyDailyMetrics(
                    boothIds, startDateTime, endDateTime);
            for (Object[] row : rows) {
                String dateKey = row[0].toString();
                long views = ((Number) row[1]).longValue();
                long interactions = ((Number) row[2]).longValue();
                chart.add(ExhibitorDashboardOverviewDTO.ChartPoint.builder()
                        .date(dateKey).views(views).interactions(interactions).build());
                totalViews += views;
                totalInteractions += interactions;
            }
        }

        // Chặng 4: tin nhắn chưa đọc + số gian hàng đang hoạt động
        long unreadMessages = chatMessageRepository.countUnreadForExhibitor(exhibitor.getId());
        long activeBoothsCount = booths.stream().filter(b -> b.getStatus() == BoothStatus.PUBLISHED).count();

        // Chặng 5: top 3 gian hàng nhiều lượt xem nhất trong kỳ
        List<BoothViewCount> boothViewCounts = new ArrayList<>();
        for (Booth booth : booths) {
            long views = analyticsEventRepository.countBoothViews(booth.getId(), startDateTime, endDateTime);
            boothViewCounts.add(new BoothViewCount(booth, views));
        }
        boothViewCounts.sort((a, b) -> Long.compare(b.views(), a.views()));
        List<ExhibitorDashboardOverviewDTO.TopBooth> topBooths = boothViewCounts.stream()
                .limit(3)
                .map(bvc -> ExhibitorDashboardOverviewDTO.TopBooth.builder()
                        .id(bvc.booth().getId().toString())
                        .name(bvc.booth().getName())
                        .status(boothStatusLabel(bvc.booth().getStatus()))
                        .views(bvc.views())
                        .build())
                .toList();

        ExhibitorDashboardOverviewDTO.Metrics metrics = ExhibitorDashboardOverviewDTO.Metrics.builder()
                .totalViews(totalViews)
                .totalInteractions(totalInteractions)
                .unreadMessages(unreadMessages)
                .activeBoothsCount(activeBoothsCount)
                .totalBoothsCount(booths.size())
                .build();

        return ExhibitorDashboardOverviewDTO.builder()
                .startDate(rangeStart.toString())
                .endDate(rangeEnd.toString())
                .metrics(metrics)
                .chart(chart)
                .topBooths(topBooths)
                .build();
    }
}
