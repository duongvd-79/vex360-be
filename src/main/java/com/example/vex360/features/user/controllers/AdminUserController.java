package com.example.vex360.features.user.controllers;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateRoleRequest;
import com.example.vex360.features.user.dtos.request.UpdateStatusRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.dtos.response.UserSummaryResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Users", description = "Admin quan ly user, role va trang thai tai khoan")
public class AdminUserController {

    private final UserService userService;

    @PostMapping
    @Operation(
            summary = "Admin tao user moi",
            description = "Tao tai khoan moi, sinh mat khau tam thoi, luu mat khau da hash va gui thong tin dang nhap qua email.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponseDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user));
    }

    @GetMapping
    @Operation(
            summary = "Admin lay danh sach user",
            description = "Xem danh sach user co phan trang, ho tro loc theo keyword, role va status.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponseDTO>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "email") Pageable pageable) {
        PageResponse<UserResponseDTO> users = userService.getUsers(keyword, role, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Admin xem thong ke user",
            description = "Tra ve tong so user, so tai khoan active, so tai khoan admin va so tai khoan pending.")
    public ResponseEntity<ApiResponse<UserSummaryResponseDTO>> getUserSummary() {
        UserSummaryResponseDTO summary = userService.getUserSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Admin xem chi tiet user",
            description = "Lay thong tin chi tiet cua mot user theo ID.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserById(@PathVariable UUID id) {
        UserResponseDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/{id}/role")
    @Operation(
            summary = "Admin cap nhat role user",
            description = "Thay doi single role cua user theo ID.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        UserResponseDTO user = userService.updateRole(id, request.getRole());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Admin cap nhat trang thai user",
            description = "Thay doi trang thai tai khoan va thu hoi refresh token hien co cua user.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        UserResponseDTO user = userService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
