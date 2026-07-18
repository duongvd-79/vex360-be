package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.entities.RegistrationToken;
import com.example.vex360.features.auth.mapper.AuthMapper;
import com.example.vex360.features.auth.repositories.RegistrationTokenRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Mock
    private UserService userService;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private RegistrationTokenRepository tokenRepository;
    @Mock
    private MailService mailService;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(
                userService,
                authMapper,
                tokenRepository,
                mailService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "http://localhost:8080");
        TokenEncryptionUtils.setKey("TestSecretKey1234");
    }

    @Test
    void register_CreatesPendingUserAndVerificationToken() {
        RegisterRequest request = new RegisterRequest();
        CreateUserRequest createRequest = CreateUserRequest.builder().email("new@example.com").build();
        User user = User.builder().id(UUID.randomUUID()).email("new@example.com").build();
        when(authMapper.toCreateUserRequest(request)).thenReturn(createRequest);
        when(userService.createUser(createRequest, UserStatus.PENDING)).thenReturn(user);

        service.register(request);

        verify(tokenRepository).deleteByUser(user);
        verify(tokenRepository).save(any(RegistrationToken.class));
        verify(mailService).sendRegistrationVerificationEmail(eq("new@example.com"), anyString());
    }

    @Test
    void verifyRegistration_ActivatesUserAndConsumesToken() {
        String rawToken = "registration-token";
        User user = User.builder().id(UUID.randomUUID()).build();
        RegistrationToken token = RegistrationToken.builder()
                .token(rawToken)
                .expiryDate(NOW.plusSeconds(60))
                .user(user)
                .build();
        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(token));

        service.verifyRegistration(TokenEncryptionUtils.encrypt(rawToken));

        verify(userService).updateStatus(user.getId(), UserStatus.ACTIVE);
        verify(tokenRepository).delete(token);
    }

    @Test
    void verifyRegistration_ExpiredTokenIsRejectedAndDeleted() {
        String rawToken = "expired-token";
        RegistrationToken token = RegistrationToken.builder()
                .token(rawToken)
                .expiryDate(NOW.minusSeconds(1))
                .user(User.builder().id(UUID.randomUUID()).build())
                .build();
        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(token));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.verifyRegistration(TokenEncryptionUtils.encrypt(rawToken)));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verify(tokenRepository).delete(token);
        verify(userService, never()).updateStatus(any(), any());
    }
}
