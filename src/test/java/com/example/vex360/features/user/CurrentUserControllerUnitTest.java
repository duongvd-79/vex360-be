package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.vex360.features.user.controllers.CurrentUserController;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.ApiResponse;

@ExtendWith(MockitoExtension.class)
class CurrentUserControllerUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private UserDetails userDetails;

    private CurrentUserController currentUserController;
    private UserResponseDTO sampleUserDto;

    @BeforeEach
    void setUp() {
        currentUserController = new CurrentUserController(userService);
        sampleUserDto = new UserResponseDTO();
        sampleUserDto.setId(UUID.randomUUID());
        sampleUserDto.setEmail("current@example.com");
        sampleUserDto.setFullName("Current User");
        sampleUserDto.setRole("VISITOR");
        sampleUserDto.setStatus("ACTIVE");
    }

    @Test
    void getCurrentUser_Success() {
        when(userDetails.getUsername()).thenReturn("current@example.com");
        when(userService.getCurrentUser("current@example.com")).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = currentUserController.getCurrentUser(userDetails);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).getCurrentUser("current@example.com");
    }

    @Test
    void updateCurrentUserProfile_Success() {
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", "0912345678", "new.png");
        when(userDetails.getUsername()).thenReturn("current@example.com");
        when(userService.updateCurrentUserProfile("current@example.com", request)).thenReturn(sampleUserDto);

        ResponseEntity<ApiResponse<UserResponseDTO>> response = currentUserController.updateCurrentUserProfile(userDetails, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleUserDto, response.getBody().data());
        verify(userService).updateCurrentUserProfile("current@example.com", request);
    }

    @Test
    void changeCurrentUserPassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass123!");
        when(userDetails.getUsername()).thenReturn("current@example.com");

        ResponseEntity<ApiResponse<Void>> response = currentUserController.changeCurrentUserPassword(userDetails, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).changeCurrentUserPassword("current@example.com", request);
    }
}
