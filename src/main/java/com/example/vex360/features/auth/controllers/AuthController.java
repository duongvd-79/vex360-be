package com.example.vex360.features.auth.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.dtos.request.ForgotPasswordRequest;
import com.example.vex360.features.auth.dtos.request.GoogleCallbackRequest;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;

    @PostMapping("/register")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return createSuccessResponse(authService.login(request));
    }

    @PostMapping("/google/callback")
    public ApiResponse<TokenResponse> googleCallback(@Valid @RequestBody GoogleCallbackRequest request) {
        return createSuccessResponse(authService.loginWithGoogle(request.getCode()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(@RequestParam("token") String token) {
        return createSuccessResponse(authService.refreshToken(token));
    }

    @PostMapping("/logout")
    public void logout(@RequestParam("token") String token) {
        authService.logout(token);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return createSuccessResponse(null, "Nếu email tồn tại trong hệ thống, mã khôi phục mật khẩu đã được gửi!");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null, "Đặt lại mật khẩu thành công!");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUser(), request);
        return ApiResponse.success(null, "Yêu cầu thay đổi mật khẩu đã được gửi đến email của bạn!");
    }

    @PostMapping("/confirm-change-password")
    public ApiResponse<Void> confirmChangePassword(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword) {
        authService.confirmPasswordChange(token, newPassword);
        return ApiResponse.success(null, "Thay đổi mật khẩu thành công!");
    }
}
