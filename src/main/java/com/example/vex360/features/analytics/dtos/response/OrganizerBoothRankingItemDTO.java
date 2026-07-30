package com.example.vex360.features.analytics.dtos.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Một dòng trong bảng xếp hạng gian hàng của ban tổ chức: gộp mọi gian hàng
 * thuộc các triển lãm do organizer đang quản lý, để so sánh mức độ thu hút giữa
 * các gian hàng và giữa các triển lãm với nhau.
 */
@Data
@Builder
public class OrganizerBoothRankingItemDTO {
    private UUID boothId;
    private String boothName;
    private String exhibitionUuid;
    private String exhibitionName;
    /** Số khách khác nhau đã xem gian hàng trong kỳ. */
    private long visitorCount;
    /** Tổng lượt xem gian hàng (BOOTH_VIEW) trong kỳ. */
    private long viewCount;
    /** Số event CHAT_INITIATED hợp lệ trong kỳ; mỗi event mở chat là một phiên. */
    private long chatCount;
    /** Thời gian trung bình khách ở trong gian hàng (phút). */
    private double averageTimeInBoothMinutes;
}
