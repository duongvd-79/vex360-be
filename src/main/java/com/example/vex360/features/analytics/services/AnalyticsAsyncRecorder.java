package com.example.vex360.features.analytics.services;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.vex360.features.analytics.dtos.request.RecordAnalyticsEventRequest;
import com.example.vex360.features.user.entities.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ghi analytics event ở luồng nền để KHÔNG chặn request của khách.
 *
 * <p>
 * Tách thành bean riêng (thay vì đặt {@code @Async} thẳng lên
 * {@code recordEvent}) vì:
 * <ul>
 * <li>{@code @Async} phải chạy "ngoài cùng" để cả phần {@code @Transactional}
 * của {@code recordEvent} diễn ra trong luồng nền. Gọi chéo bean giúp proxy
 * transaction áp dụng đúng trong luồng async.</li>
 * <li>Lỗi (vd booth không tồn tại) chỉ ghi log, không ảnh hưởng khách — vì
 * đây là tính năng phụ, "bắn rồi quên".</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAsyncRecorder {

    private final AnalyticsService analyticsService;

    @Async
    public void recordEventAsync(User user, RecordAnalyticsEventRequest request) {
        try {
            analyticsService.recordEvent(user, request);
        } catch (Exception e) {
            log.warn("Ghi analytics event thất bại (bỏ qua): {}", e.getMessage());
        }
    }
}
