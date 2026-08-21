package com.example.vex360.features.analytics.dtos.request;

import java.util.UUID;

import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecordAnalyticsEventRequest {

    @NotNull(message = "Loại sự kiện không được để trống")
    private AnalyticsEventType eventType;

    /** UUID của triển lãm — cho ENTER_EXHIBITION / LEAVE_EXHIBITION. */
    private UUID exhibitionUuid;

    /** ID gian hàng — cho BOOTH_VIEW / PRODUCT_CLICK / CHAT_INITIATED. */
    private UUID boothId;

    /** ID sản phẩm — cho PRODUCT_CLICK. */
    private UUID productId;

    /** Thời lượng (giây) — cho LEAVE_EXHIBITION. */
    @PositiveOrZero(message = "Thời lượng phải lớn hơn hoặc bằng 0.")
    private Integer durationSeconds;

    /** Dữ liệu phụ tuỳ ý (JSON) — không bắt buộc. */
    @Size(max = 10000, message = "Metadata không được vượt quá 10000 ký tự.")
    private String metadataJson;

    @JsonIgnore
    @AssertTrue(message = "Triển lãm không được để trống với loại sự kiện này.")
    public boolean isExhibitionReferenceValid() {
        return eventType == null
                || (eventType != AnalyticsEventType.ENTER_EXHIBITION
                        && eventType != AnalyticsEventType.LEAVE_EXHIBITION)
                || exhibitionUuid != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Gian hàng không được để trống với loại sự kiện này.")
    public boolean isBoothReferenceValid() {
        return eventType == null
                || (eventType != AnalyticsEventType.BOOTH_VIEW
                        && eventType != AnalyticsEventType.BOOTH_LEAVE
                        && eventType != AnalyticsEventType.PRODUCT_CLICK
                        && eventType != AnalyticsEventType.CHAT_INITIATED)
                || boothId != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Sản phẩm không được để trống với sự kiện PRODUCT_CLICK.")
    public boolean isProductReferenceValid() {
        return eventType != AnalyticsEventType.PRODUCT_CLICK || productId != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Thời lượng không được để trống với loại sự kiện này.")
    public boolean isDurationValid() {
        return eventType == null
                || (eventType != AnalyticsEventType.LEAVE_EXHIBITION
                        && eventType != AnalyticsEventType.BOOTH_LEAVE)
                || durationSeconds != null;
    }
}
