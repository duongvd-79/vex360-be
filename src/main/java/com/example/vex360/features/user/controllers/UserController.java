package com.example.vex360.features.user.controllers;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.request.UpdateRoleRequest;
import com.example.vex360.features.user.dtos.request.UpdateStatusRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.config.security.CustomUserDetails;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponseDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Create user successfully", user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponseDTO>>> getUsers(
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "email") Pageable pageable) {
        PageResponse<UserResponseDTO> users = userService.getUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success("Get users successfully", users));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponseDTO user = userService.getCurrentUser(userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success("Get current user successfully", user));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateCurrentUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        UserResponseDTO user = userService.updateCurrentUserProfile(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success("Update profile successfully", user));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeCurrentUserPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success("Change password successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserById(@PathVariable UUID id) {
        UserResponseDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("Get user successfully", user));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        UserResponseDTO user = userService.updateRole(id, request.getRole());
        return ResponseEntity.ok(ApiResponse.success("Update role successfully", user));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        UserResponseDTO user = userService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Update status successfully", user));
    }
}
