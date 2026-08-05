package com.example.vex360.features.auth.jobs;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.repositories.RegistrationTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthDataCleanupJob {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RegistrationTokenRepository registrationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "${app.auth.token-cleanup-cron:0 15 0 * * *}", zone = "UTC")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now(clock);
        long resetTokensDeleted = passwordResetTokenRepository.deleteByExpiryDateBefore(now);
        long regTokensDeleted = registrationTokenRepository.deleteByExpiryDateBefore(now);
        long refreshTokensDeleted = refreshTokenRepository.deleteByExpiryDateBefore(now);

        log.info("Expired tokens cleanup completed: resetTokens={}, registrationTokens={}, refreshTokens={}",
                resetTokensDeleted, regTokensDeleted, refreshTokensDeleted);
    }
}
