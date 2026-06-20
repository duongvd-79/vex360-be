package com.example.vex360.features.auth.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller exposing authentication endpoints.
 * Integrates input validation using Jakarta Validation (@Valid) to enforce boundary security,
 * and standardizes responses using the generic ApiResponse structure.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Quản lý xác thực, đăng ký, khôi phục mật khẩu và vòng đời token")
public class AuthController extends BaseController {

    private final AuthService authService;

    @Value("${app.reset-password.frontend-url}")
    private String resetPasswordFrontendUrl;

    @Value("${app.registration.frontend-url}")
    private String registrationFrontendUrl;

    /**
     * Endpoint for user registration.
     * Enforces input validations defined in RegisterRequest.
     *
     * @param request the registration details
     */
    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo một tài khoản người dùng mới trong hệ thống.")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /**
     * Endpoint to verify user registration using registration token.
     *
     * @param token the encrypted verification token
     * @return redirection ResponseEntity to the frontend login form
     */
    @GetMapping("/register/verify")
    @Operation(summary = "Xác thực tài khoản đăng ký mới", description = "Giải mã và kiểm tra token xác thực đăng ký. Nếu hợp lệ, kích hoạt tài khoản thành ACTIVE và chuyển hướng người dùng về trang đăng nhập ở frontend.")
    public ResponseEntity<Void> verifyRegistration(@RequestParam("token") String token) {
        authService.verifyRegistration(token);
        String redirectUrl = registrationFrontendUrl + "?verified=true";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    /**
     * Endpoint for user login.
     * Generates a stateless Access Token and a rotated Refresh Token session key.
     *
     * @param request the credentials
     * @return unified API response containing token details
     */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập tài khoản", description = "Xác thực email và mật khẩu của người dùng, trả về Access Token (stateless) và Refresh Token (stateful).")
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
    @Operation(summary = "Làm mới Access Token", description = "Sử dụng Refresh Token hợp lệ để nhận cặp token mới. Áp dụng cơ chế xoay vòng Refresh Token (Rotation) và phát hiện tấn công phát lại (Replay Detection).")
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
    @Operation(summary = "Đăng xuất tài khoản", description = "Thu hồi Refresh Token hiện tại và đưa Access Token đang dùng vào danh sách đen (blacklist).")
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
    @Operation(summary = "Yêu cầu khôi phục mật khẩu", description = "Gửi một email chứa liên kết khôi phục mật khẩu đã được mã hóa AES. Áp dụng cơ chế chống dò quét email người dùng.")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return createSuccessResponse(null, "Nếu email tồn tại trong hệ thống, mã khôi phục mật khẩu đã được gửi!");
    }

    /**
     * Endpoint to validate reset token and redirect user to the password reset page.
     *
     * @param token the encrypted reset token
     * @return redirection ResponseEntity to the frontend reset password form
     */
    @GetMapping("/reset-password/validate")
    @Operation(summary = "Xác thực token khôi phục mật khẩu", description = "Giải mã và kiểm tra thời hạn sử dụng của token. Nếu hợp lệ, tự động chuyển hướng người dùng về trang nhập mật khẩu mới ở frontend.")
    public ResponseEntity<Void> validateResetToken(@RequestParam("token") String token) {
        authService.validateResetToken(token);
        String redirectUrl = resetPasswordFrontendUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    /**
     * Endpoint to confirm password reset using reset token.
     *
     * @param request containing the reset token and new password
     * @return unified API response confirming success
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu mới", description = "Sử dụng token khôi phục đã giải mã để lưu mật khẩu mới, hủy bỏ token khôi phục cũ và thu hồi toàn bộ các phiên làm việc hiện tại của tài khoản.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null, "Đặt lại mật khẩu thành công!");
    }

    /**
     * Endpoint to change password.
     * Changes password directly and sends notification email.
     *
     * @param userDetails the current authenticated principal
     * @param request containing old and new passwords
     * @return unified API response confirming success
     */
    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu trực tiếp", description = "Thay đổi mật khẩu mới trực tiếp cho người dùng hiện tại đang đăng nhập. Hệ thống sẽ cập nhật mật khẩu, hủy toàn bộ các phiên hoạt động khác và gửi email thông báo.")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUser(), request);
        return ApiResponse.success(null, "Thay đổi mật khẩu thành công!");
    }
}
