package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.dtos.response.UserSummaryResponseDTO;
import com.example.vex360.features.user.mapper.UserMapper;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private UserService userService;
    private UUID userId;
    private User sampleUser;

    @BeforeEach
    void setup() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        userService = new UserService(userRepository, passwordEncoder, userMapper, refreshTokenRepository);
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("encodedPassword")
                .fullName("Old Name")
                .phoneNumber("123")
                .role(Role.VISITOR)
                .avatarUrl("old.png")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createUserDefaultsRoleToVisitorAndEncodesPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "new@example.com", "Password123!", "New User", "456", null, "avatar.png");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals(Role.VISITOR, savedUser.getRole());
        assertEquals("encodedNewPassword", savedUser.getPassword());
        assertEquals("new@example.com", response.getEmail());
    }

    @Test
    void createUserThrowsWhenEmailExists() {
        CreateUserRequest request = new CreateUserRequest(
                "new@example.com", "Password123!", "New User", null, Role.ADMIN, null);

        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> userService.createUser(request));

        assertSame(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserByIdThrowsWhenMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getUserById(userId));

        assertSame(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateCurrentUserProfileIgnoresNullFields() {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", null, "new.png");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateCurrentUserProfile(sampleUser, request);

        assertEquals("New Name", sampleUser.getFullName());
        assertEquals("123", sampleUser.getPhoneNumber());
        assertEquals("new.png", sampleUser.getAvatarUrl());
        assertEquals("New Name", response.getFullName());
        assertEquals("123", response.getPhoneNumber());
    }

    @Test
    void updateCurrentUserProfileReturnsUpdatedPhoneNumber() {
        UpdateProfileRequest request = new UpdateProfileRequest(null, "0912345678", null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateCurrentUserProfile(sampleUser, request);

        assertEquals("0912345678", sampleUser.getPhoneNumber());
        assertEquals("0912345678", response.getPhoneNumber());
    }

    @Test
    void changeCurrentUserPasswordThrowsWhenOldPasswordDoesNotMatch() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "NewPassword123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> userService.changeCurrentUserPassword(sampleUser, request));

        assertSame(ErrorCode.OLDPASSWORD_FAILED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeCurrentUserPasswordEncodesAndSavesNewPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("old", "NewPassword123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

        userService.changeCurrentUserPassword(sampleUser, request);

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void updateRoleAndStatus() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO roleResponse = userService.updateRole(userId, Role.ADMIN);
        UserResponseDTO statusResponse = userService.updateStatus(userId, UserStatus.BLOCKED);

        assertEquals("ADMIN", roleResponse.getRole());
        assertEquals("BLOCKED", statusResponse.getStatus());
    }

    @Test
    void getUsersReturnsPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers("user", Role.VISITOR, UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(sampleUser), pageable, 1));

        PageResponse<UserResponseDTO> response = userService.getUsers(" user ", Role.VISITOR, UserStatus.ACTIVE, pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1, response.getTotalElements());
        verify(userRepository).searchUsers(eq("user"), eq(Role.VISITOR), eq(UserStatus.ACTIVE), eq(pageable));
    }

    @Test
    void getUserSummaryReturnsCounts() {
        when(userRepository.count()).thenReturn(1284L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(1102L);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(12L);
        when(userRepository.countByStatus(UserStatus.PENDING)).thenReturn(45L);

        UserSummaryResponseDTO response = userService.getUserSummary();

        assertEquals(1284L, response.getTotalUsers());
        assertEquals(1102L, response.getActiveUsers());
        assertEquals(12L, response.getAdminUsers());
        assertEquals(45L, response.getPendingUsers());
    }
}
