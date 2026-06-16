package com.example.vex360.features.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;

import com.example.vex360.TestcontainersConfiguration;
import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.repositories.PasswordResetTokenRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.repositories.UserRepository;

import org.junit.jupiter.api.condition.EnabledIf;
import com.fasterxml.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIf("isDockerAvailable")
public class AuthFlowIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testFullAuthFlow() throws Exception {
        // 1. Register User
        RegisterRequest registerReq = new RegisterRequest(
            "test@example.com",
            "StrongPassword123!",
            "John Doe",
            "123456789",
            "ROLE_USER",
            null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        assertTrue(userRepository.existsByEmail("test@example.com"));

        // 2. Login User
        LoginRequest loginReq = new LoginRequest("test@example.com", "StrongPassword123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        TokenResponse tokenResponse = objectMapper.readValue(responseBody, TokenResponse.class);
        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // 3. Token Refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .param("token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String refreshResponseBody = refreshResult.getResponse().getContentAsString();
        TokenResponse refreshResponse = objectMapper.readValue(refreshResponseBody, TokenResponse.class);
        String newAccessToken = refreshResponse.getAccessToken();
        String newRefreshToken = refreshResponse.getRefreshToken();

        assertNotNull(newAccessToken);
        assertNotNull(newRefreshToken);
        assertNotEquals(refreshToken, newRefreshToken);

        // 4. Logout User
        mockMvc.perform(post("/api/v1/auth/logout")
                .param("token", newRefreshToken))
                .andExpect(status().isOk());

        // Verify refresh token is deleted
        assertFalse(refreshTokenRepository.findByToken(newRefreshToken).isPresent());

        // 5. Forgot Password
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("test@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Retrieve token from DB
        var resetTokenOpt = passwordResetTokenRepository.findAll().stream().findFirst();
        assertTrue(resetTokenOpt.isPresent());
        String resetToken = resetTokenOpt.get().getToken();

        // 6. Reset Password
        ResetPasswordRequest resetReq = new ResetPasswordRequest(resetToken, "NewPassword123!");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // Verify login works with new password
        LoginRequest loginNewReq = new LoginRequest("test@example.com", "NewPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginNewReq)))
                .andExpect(status().isOk());

        // 7. Request Change Password (when authenticated)
        // Login again to get active access token
        MvcResult reLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginNewReq)))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse reLoginToken = objectMapper.readValue(reLoginResult.getResponse().getContentAsString(), TokenResponse.class);

        ChangePasswordRequest changeReq = new ChangePasswordRequest("NewPassword123!", "AnotherPassword123!");
        mockMvc.perform(post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + reLoginToken.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk());

        // Retrieve token from DB
        var changeTokenOpt = passwordResetTokenRepository.findAll().stream().findFirst();
        assertTrue(changeTokenOpt.isPresent());
        String changeToken = changeTokenOpt.get().getToken();

        // 8. Confirm Password Change (through mail token verification)
        mockMvc.perform(post("/api/v1/auth/confirm-change-password")
                .param("token", changeToken)
                .param("newPassword", "AnotherPassword123!"))
                .andExpect(status().isOk());

        // Verify login works with the confirmed new password
        LoginRequest loginFinalReq = new LoginRequest("test@example.com", "AnotherPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginFinalReq)))
                .andExpect(status().isOk());
    }
}
