package com.example.vex360.features.user.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "User dang dang nhap xem va cap nhat ho so ca nhan")
public class CurrentUserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(
            summary = "Lay thong tin ca nhan",
            description = "Tra ve thong tin ho so cua user dang dang nhap.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponseDTO user = userService.getCurrentUser(userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Cap nhat ho so ca nhan",
            description = "Cap nhat fullName, phoneNumber va avatarUrl cua user dang dang nhap.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateCurrentUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        UserResponseDTO user = userService.updateCurrentUserProfile(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/me/password")
    @Operation(
            summary = "Doi mat khau ca nhan",
            description = "User dang dang nhap doi mat khau bang mat khau cu va mat khau moi.")
    public ResponseEntity<ApiResponse<Void>> changeCurrentUserPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
