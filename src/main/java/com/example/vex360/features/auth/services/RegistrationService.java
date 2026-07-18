package com.example.vex360.features.auth.services;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Manages pending account registration and email-based account activation.
 */
@Service
public class RegistrationService {

    private final UserService userService;
    private final AuthMapper authMapper;
    private final RegistrationTokenRepository tokenRepository;
    private final MailService mailService;
    private final Clock clock;
    private final String backendBaseUrl;

    public RegistrationService(
            UserService userService,
            AuthMapper authMapper,
            RegistrationTokenRepository tokenRepository,
            MailService mailService,
            Clock clock,
            @Value("${app.backend.base-url}") String backendBaseUrl) {
        this.userService = userService;
        this.authMapper = authMapper;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.clock = clock;
        this.backendBaseUrl = backendBaseUrl;
    }

    /**
     * Creates a pending user, replaces any previous verification token, and sends
     * a verification link valid for one day.
     *
     * @param request account registration data
     */
    @Transactional
    public void register(RegisterRequest request) {
        CreateUserRequest createRequest = authMapper.toCreateUserRequest(request);
        User user = userService.createUser(createRequest, UserStatus.PENDING);

        tokenRepository.deleteByUser(user);
        String tokenValue = UUID.randomUUID().toString();
        tokenRepository.save(RegistrationToken.builder()
                .token(tokenValue)
                .expiryDate(Instant.now(clock).plus(1, ChronoUnit.DAYS))
                .user(user)
                .build());

        String encryptedToken = TokenEncryptionUtils.encrypt(tokenValue);
        String verifyUrl = backendBaseUrl + "/api/v1/auth/register/verify?token=" + encryptedToken;
        mailService.sendRegistrationVerificationEmail(user.getEmail(), verifyUrl);
    }

    /**
     * Activates the account associated with a valid encrypted verification token
     * and consumes the token.
     *
     * @param encryptedToken encrypted registration token from the verification link
     * @throws AppException when the token is missing or expired
     */
    @Transactional(noRollbackFor = AppException.class)
    public void verifyRegistration(String encryptedToken) {
        String rawToken = TokenEncryptionUtils.decrypt(encryptedToken);
        RegistrationToken registrationToken = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (registrationToken.getExpiryDate().isBefore(Instant.now(clock))) {
            tokenRepository.delete(registrationToken);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        userService.updateStatus(registrationToken.getUser().getId(), UserStatus.ACTIVE);
        tokenRepository.delete(registrationToken);
    }
}
