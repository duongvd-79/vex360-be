package com.example.vex360.features.analytics.dtos.request;

import java.util.UUID;

import com.example.vex360.features.analytics.enums.AnalyticsEventType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecordAnalyticsEventRequest {

    @NotNull(message = "Loại sự kiện không được để trống")
    private AnalyticsEventType eventType;

    /** UUID của triển lãm — cho ENTER_EXHIBITION / LEAVE_EXHIBITION. */
    private String exhibitionUuid;

    /** ID gian hàng — cho BOOTH_VIEW / PRODUCT_CLICK / CHAT_INITIATED. */
    private UUID boothId;

    /** ID sản phẩm — cho PRODUCT_CLICK. */
    private UUID productId;

    /** Thời lượng (giây) — cho LEAVE_EXHIBITION. */
    private Integer durationSeconds;

    /** Dữ liệu phụ tuỳ ý (JSON) — không bắt buộc. */
    private String metadataJson;
}
