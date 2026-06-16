package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.auth.mapper.AuthMapper;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.auth.services.impl.AuthServiceImpl;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import com.example.vex360.shared.config.jwt.JwtService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplUnitTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailService mailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("John Doe")
                .role(Role.VISITOR)
                .build();
    }

    @Test
    public void testRegister_Success() {
        RegisterRequest request = new RegisterRequest(
                "test@example.com", "Password123!", "John Doe", "123456");
        UserRequestDTO mappedDto = new UserRequestDTO();
        mappedDto.setEmail(request.getEmail());

        when(authMapper.toUserRequestDTO(request)).thenReturn(mappedDto);

        authService.register(request);

        verify(userService, times(1)).createUser(mappedDto);
    }

    @Test
    public void testLogin_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "Password123!");

        when(userService.getUserByEmail(request.getEmail())).thenReturn(sampleUser);
        when(passwordEncoder.matches(request.getPassword(), sampleUser.getPassword())).thenReturn(true);
        when(jwtProvider.generateToken(any(CustomUserDetails.class)))
                .thenReturn("mockedAccessToken");

        TokenResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mockedAccessToken", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        verify(refreshTokenRepository, times(1)).deleteByUser(sampleUser);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    public void testLogin_InvalidPassword_ThrowsUnauthenticated() {
        LoginRequest request = new LoginRequest("test@example.com", "WrongPassword");

        when(userService.getUserByEmail(request.getEmail())).thenReturn(sampleUser);
        when(passwordEncoder.matches(request.getPassword(), sampleUser.getPassword())).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    public void testRefreshToken_Success() {
        String tokenStr = "valid-token";
        RefreshToken existingToken = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(sampleUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(existingToken));
        when(jwtProvider.generateToken(any(CustomUserDetails.class)))
                .thenReturn("newAccessToken");

        TokenResponse response = authService.refreshToken(tokenStr);

        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
        assertNotEquals(tokenStr, response.getRefreshToken());
        verify(refreshTokenRepository, times(1)).save(existingToken);
    }

    @Test
    public void testRefreshToken_Expired_ThrowsUnauthenticated() {
        String tokenStr = "expired-token";
        RefreshToken existingToken = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().minusSeconds(10))
                .user(sampleUser)
                .build();

        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(existingToken));

        AppException exception = assertThrows(AppException.class, () -> authService.refreshToken(tokenStr));
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verify(refreshTokenRepository, times(1)).delete(existingToken);
    }

    @Test
    public void testLogout_Success() {
        String tokenStr = "some-token";
        RefreshToken existingToken = new RefreshToken();
        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(existingToken));

        authService.logout(tokenStr);

        verify(refreshTokenRepository, times(1)).delete(existingToken);
    }

    @Test
    public void testForgotPassword_UserExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userService.getUserByEmail(request.getEmail())).thenReturn(sampleUser);

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, times(1)).deleteByUser(sampleUser);
        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(mailService, times(1)).sendForgotPasswordEmail(eq("test@example.com"), any(String.class));
    }

    @Test
    public void testForgotPassword_UserDoesNotExist_FailsSilently() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("missing@example.com");

        when(userService.getUserByEmail(request.getEmail())).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

        assertDoesNotThrow(() -> authService.forgotPassword(request));
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(mailService, never()).sendForgotPasswordEmail(any(), any());
    }

    @Test
    public void testResetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "NewPassword123!");
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token("reset-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(sampleUser)
                .build();

        when(passwordResetTokenRepository.findByToken(request.getToken())).thenReturn(Optional.of(resetToken));

        authService.resetPassword(request);

        verify(userService, times(1)).updatePassword(sampleUser, "NewPassword123!");
        verify(passwordResetTokenRepository, times(1)).delete(resetToken);
        verify(refreshTokenRepository, times(1)).deleteByUser(sampleUser);
    }

    @Test
    public void testChangePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("encodedPassword", "NewPassword123!");

        when(passwordEncoder.matches(request.getOldPassword(), sampleUser.getPassword())).thenReturn(true);

        authService.changePassword(sampleUser, request);

        verify(passwordResetTokenRepository, times(1)).deleteByUser(sampleUser);
        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(mailService, times(1)).sendPasswordChangeVerificationEmail(eq("test@example.com"), any(String.class));
    }

    @Test
    public void testConfirmPasswordChange_Success() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("change-token")
                .expiryDate(Instant.now().plusSeconds(1800))
                .user(sampleUser)
                .build();

        when(passwordResetTokenRepository.findByToken("change-token")).thenReturn(Optional.of(token));

        authService.confirmPasswordChange("change-token", "NewPassword123!");

        verify(userService, times(1)).updatePassword(sampleUser, "NewPassword123!");
        verify(passwordResetTokenRepository, times(1)).delete(token);
        verify(refreshTokenRepository, times(1)).deleteByUser(sampleUser);
    }
}
