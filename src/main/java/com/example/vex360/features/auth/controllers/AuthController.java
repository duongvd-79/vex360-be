package com.example.vex360.features.auth.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.auth.dtos.request.ResetPasswordRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.auth.services.AuthService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing authentication endpoints.
 * Integrates input validation using Jakarta Validation (@Valid) to enforce boundary security,
 * and standardizes responses using the generic ApiResponse structure.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;

    /**
     * Endpoint for user registration.
     * Enforces input validations defined in RegisterRequest.
     *
     * @param request the registration details
     */
    @PostMapping("/register")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /**
     * Endpoint for user login.
     * Generates a stateless Access Token and a rotated Refresh Token session key.
     *
     * @param request the credentials
     * @return unified API response containing token details
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return createSuccessResponse(authService.login(request));
    }

    /**
     * Endpoint for rotating a refresh token.
     * Validates the refresh token and issues a new access token along with a rotated refresh token.
     *
     * @param token the current refresh token
     * @return unified API response containing the new token details
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(@RequestParam("token") String token) {
        return createSuccessResponse(authService.refreshToken(token));
    }

    /**
     * Endpoint for user logout.
     * Invalidate refresh token session and blacklists active access token.
     *
     * @param token the refresh token to revoke
     */
    @PostMapping("/logout")
    public void logout(@RequestParam("token") String token) {
        authService.logout(token);
    }

    /**
     * Endpoint to request a forgot password link.
     * Prevents email enumeration by returning a generic success message regardless of email existence.
     *
     * @param request containing the user email
     * @return unified API response confirming initiation
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return createSuccessResponse(null, "Nếu email tồn tại trong hệ thống, mã khôi phục mật khẩu đã được gửi!");
    }

    /**
     * Endpoint to confirm password reset using reset token.
     *
     * @param request containing the reset token and new password
     * @return unified API response confirming success
     */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null, "Đặt lại mật khẩu thành công!");
    }

    /**
     * Endpoint to request password change.
     * Sends verification link to user mail after verifying old credentials.
     *
     * @param userDetails the current authenticated principal
     * @param request containing old and new passwords
     * @return unified API response confirming initiation
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUser(), request);
        return ApiResponse.success(null, "Yêu cầu thay đổi mật khẩu đã được gửi đến email của bạn!");
    }

    /**
     * Endpoint to confirm password change using verification token.
     *
     * @param token the confirmation token
     * @param newPassword the new password
     * @return unified API response confirming success
     */
    @PostMapping("/confirm-change-password")
    public ApiResponse<Void> confirmChangePassword(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword) {
        authService.confirmPasswordChange(token, newPassword);
        return ApiResponse.success(null, "Thay đổi mật khẩu thành công!");
    }
}
