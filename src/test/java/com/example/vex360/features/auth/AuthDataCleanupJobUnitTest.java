package com.example.vex360.features.auth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.jobs.AuthDataCleanupJob;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.repositories.RegistrationTokenRepository;
import com.example.vex360.features.user.services.UserService;

@ExtendWith(MockitoExtension.class)
class AuthDataCleanupJobUnitTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RegistrationTokenRepository registrationTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserService userService;

    private Clock clock;
    private AuthDataCleanupJob job;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-04T00:15:00Z"), ZoneId.of("UTC"));
        job = new AuthDataCleanupJob(
                passwordResetTokenRepository,
                registrationTokenRepository,
                refreshTokenRepository,
                userService,
                clock);
    }

    @Test
    void cleanupExpiredTokens_DeletesExpiredTokensWithSameCutoff() {
        Instant expectedCutoff = Instant.parse("2026-08-04T00:15:00Z");
        when(passwordResetTokenRepository.deleteByExpiryDateBefore(expectedCutoff)).thenReturn(2);
        when(registrationTokenRepository.deleteByExpiryDateBefore(expectedCutoff)).thenReturn(3);
        when(refreshTokenRepository.deleteByExpiryDateBefore(expectedCutoff)).thenReturn(5);

        job.cleanupExpiredTokens();

        verify(passwordResetTokenRepository).deleteByExpiryDateBefore(expectedCutoff);
        verify(registrationTokenRepository).deleteByExpiryDateBefore(expectedCutoff);
        verify(refreshTokenRepository).deleteByExpiryDateBefore(expectedCutoff);
    }

    @Test
    void cleanupPendingUsers_DeletesUnverifiedPendingLocalUsersOlderThan7Days() {
        Instant expectedCutoff = Instant.parse("2026-08-04T00:15:00Z").minus(Duration.ofDays(7));
        when(userService.deleteUnverifiedPendingLocalUsersOlderThan(expectedCutoff))
                .thenReturn(4L);

        job.cleanupPendingUsers();

        verify(userService).deleteUnverifiedPendingLocalUsersOlderThan(expectedCutoff);
    }
}
