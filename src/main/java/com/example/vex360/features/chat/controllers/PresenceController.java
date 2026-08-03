package com.example.vex360.features.chat.controllers;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.chat.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    // FE gọi định kỳ (vd. mỗi 20s) khi có người dùng đã đăng nhập mở app, bất kể
    // đang ở màn nào,
    // để cập nhật "đang online" — xem PresenceService.
    @PostMapping("/api/v1/presence/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal CustomUserDetails userDetails) {
        presenceService.touch(userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}
