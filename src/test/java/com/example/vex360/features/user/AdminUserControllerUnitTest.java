package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.user.controllers.AdminUserController;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateRoleRequest;
import com.example.vex360.features.user.dtos.request.UpdateStatusRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerUnitTest {

    @Mock
    private UserService userService;

    private AdminUserController adminUserController;
    private UUID userId;
    private UserResponseDTO sampleUserDto;

    @BeforeEach
    void setUp() {
        adminUserController = new AdminUserController(userService);
        userId = UUID.randomUUID();
        sampleUserDto = new UserResponseDTO();
        sampleUserDto.setId(userId);
        sampleUserDto.setEmail("user@example.com");
        sampleUserDto.setFullName("User Name");
        sampleUserDto.setRole("VISITOR");
        sampleUserDto.setStatus("ACTIVE");
    }

    @Test
    void createUser_Success() {
        CreateUserRequest request = CreateUserRequest.builder()
                .email("new@example.com")
                .fullName("New User")
                .phoneNumber("0912345678")
                .role(Role.EXHIBITOR)
                .build();

        when(userService.createUser(request)).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = adminUserController.createUser(request);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).createUser(request);
    }

    @Test
    void getUsers_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<UserResponseDTO> pageResponse = PageResponse.<UserResponseDTO>builder()
                .content(List.of(sampleUserDto))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        when(userService.getUsers("keyword", Role.VISITOR, UserStatus.ACTIVE, pageable)).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<UserResponseDTO>>> response =
                adminUserController.getUsers("keyword", Role.VISITOR, UserStatus.ACTIVE, pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(pageResponse, response.getBody().data());
        verify(userService).getUsers("keyword", Role.VISITOR, UserStatus.ACTIVE, pageable);
    }

    @Test
    void getUserById_Success() {
        when(userService.getUserById(userId)).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = adminUserController.getUserById(userId);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).getUserById(userId);
    }

    @Test
    void updateRole_Success() {
        UpdateRoleRequest request = new UpdateRoleRequest(Role.EXHIBITOR);
        when(userService.updateRole(userId, Role.EXHIBITOR)).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = adminUserController.updateRole(userId, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).updateRole(userId, Role.EXHIBITOR);
    }

    @Test
    void updateStatus_Success() {
        UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.BLOCKED);
        when(userService.updateStatus(userId, UserStatus.BLOCKED)).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = adminUserController.updateStatus(userId, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).updateStatus(userId, UserStatus.BLOCKED);
    }
}
