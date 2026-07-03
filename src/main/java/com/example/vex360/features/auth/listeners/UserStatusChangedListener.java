package com.example.vex360.features.auth.listeners;

import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.events.UserStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserStatusChangedListener {

    private final RefreshTokenRepository refreshTokenRepository;

    @EventListener
    @Transactional
    public void handleUserStatusChanged(UserStatusChangedEvent event) {
        log.info("Received UserStatusChangedEvent for user: {}", event.getUser().getEmail());
        refreshTokenRepository.deleteByUser(event.getUser());
    }
}
