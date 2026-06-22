package com.example.vex360.features.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.auth.controllers.AuthController;
import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthController authController;

    @BeforeEach
    void setup() {
        authController = new AuthController(authService);
        ReflectionTestUtils.setField(authController, "resetPasswordFrontendUrl", "http://localhost:5173/reset-password");
        ReflectionTestUtils.setField(authController, "registrationFrontendUrl", "http://localhost:5173/login");

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerShouldReturnApiResponseVoid() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "register@example.com", "Password123!", "John Register", "0987654321");
        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đăng ký tài khoản thành công! Vui lòng kiểm tra email để xác thực tài khoản."));
    }

    @Test
    void logoutShouldReturnApiResponseVoid() throws Exception {
        String token = "some-refresh-token";
        doNothing().when(authService).logout(token);

        mockMvc.perform(post("/api/v1/auth/logout")
                .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công!"));
    }

    @Test
    void loginShouldReturnApiResponseToken() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        TokenResponse tokenResponse = new TokenResponse("access-token", "refresh-token", "Bearer");
        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void refreshTokenShouldReturnApiResponseToken() throws Exception {
        String token = "some-refresh-token";
        TokenResponse tokenResponse = new TokenResponse("new-access-token", "new-refresh-token", "Bearer");
        when(authService.refreshToken(token)).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void forgotPasswordShouldReturnApiResponseVoid() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Nếu email tồn tại trong hệ thống, mã khôi phục mật khẩu đã được gửi!"));
    }

    @Test
    void verifyRegistrationShouldRedirect() throws Exception {
        String token = "verify-token";
        doNothing().when(authService).verifyRegistration(token);

        mockMvc.perform(get("/api/v1/auth/register/verify")
                .param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/login?verified=true"));
    }

    @Test
    void validateResetTokenShouldRedirect() throws Exception {
        String token = "reset-token";
        doNothing().when(authService).validateResetToken(token);

        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                .param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/reset-password?token=reset-token"));
    }

    @Test
    void resetPasswordShouldReturnApiResponseVoid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "NewPassword123!");
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công!"));
    }

    @Test
    void changePasswordShouldReturnApiResponseVoid() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");
        doNothing().when(authService).changePassword(any(User.class), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Thay đổi mật khẩu thành công!"));
    }
}
