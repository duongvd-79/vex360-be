package com.example.vex360.features.chat.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Theo dõi "đang online" bằng heartbeat định kỳ từ client (không phụ thuộc vào
 * việc client
 * có đang mở đúng 1 phòng chat hay không — trước đây dùng SimpUserRegistry nên
 * chỉ biết online
 * khi có kết nối WebSocket của phòng chat, tức chỉ đúng khi người dùng đang xem
 * đúng màn chat).
 *
 * Lưu trong bộ nhớ (không cần Redis) vì chỉ chạy 1 instance; nếu scale nhiều
 * instance sau này
 * cần chuyển map này sang một store dùng chung.
 */
@Service
public class PresenceService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofSeconds(45);

    private final Map<UUID, Instant> lastSeenAt = new ConcurrentHashMap<>();

    public void touch(UUID userId) {
        lastSeenAt.put(userId, Instant.now());
    }

    public boolean isOnline(UUID userId) {
        Instant last = lastSeenAt.get(userId);
        return last != null && last.isAfter(Instant.now().minus(ONLINE_THRESHOLD));
    }
}
