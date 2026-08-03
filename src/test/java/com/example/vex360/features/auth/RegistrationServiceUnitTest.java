package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.contains;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import com.example.vex360.features.auth.services.RegistrationService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private RegistrationTokenRepository tokenRepository;
    @Mock
    private MailService mailService;

    private Clock clock;
    private RegistrationService registrationService;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneId.of("UTC"));
        registrationService = new RegistrationService(
                userService,
                authMapper,
                tokenRepository,
                mailService,
                clock,
                "http://localhost:8080"
        );

        user = User.builder()
                .id(UUID.randomUUID())
                .email("newuser@example.com")
                .role(Role.VISITOR)
                .status(UserStatus.PENDING)
                .build();
    }

    @Test
    void register_CreatesPendingUserAndSendsVerificationEmail() {
        RegisterRequest registerReq = new RegisterRequest("newuser@example.com", "Password123!", "New User", "0912345678");
        CreateUserRequest createReq = CreateUserRequest.builder().email("newuser@example.com").build();

        when(authMapper.toCreateUserRequest(registerReq)).thenReturn(createReq);
        when(userService.createUser(createReq, UserStatus.PENDING)).thenReturn(user);

        registrationService.register(registerReq);

        verify(tokenRepository).deleteByUser(user);
        verify(tokenRepository).save(any(RegistrationToken.class));
        verify(mailService).sendRegistrationVerificationEmail(eq("newuser@example.com"), contains("token="));
    }

    @Test
    void verifyRegistration_ValidToken_ActivatesUserAndDeletesToken() {
        String rawToken = "raw_reg_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        RegistrationToken token = RegistrationToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-02T10:00:00Z")) // Valid
                .build();
        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(token));

        registrationService.verifyRegistration(encryptedToken);

        verify(userService).updateStatus(user.getId(), UserStatus.ACTIVE);
        verify(tokenRepository).delete(token);
    }

    @Test
    void verifyRegistration_ExpiredToken_DeletesTokenAndThrowsAppException() {
        String rawToken = "raw_expired_reg_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        RegistrationToken token = RegistrationToken.builder()
                .token(rawToken)
                .user(user)
                .expiryDate(Instant.parse("2026-08-01T09:00:00Z")) // Expired
                .build();
        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(token));

        AppException ex = assertThrows(AppException.class, () -> registrationService.verifyRegistration(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());

        verify(tokenRepository).delete(token);
    }

    @Test
    void verifyRegistration_TokenNotFound_ThrowsAppException() {
        String rawToken = "unknown_token";
        String encryptedToken = TokenEncryptionUtils.encrypt(rawToken);

        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> registrationService.verifyRegistration(encryptedToken));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }
}
