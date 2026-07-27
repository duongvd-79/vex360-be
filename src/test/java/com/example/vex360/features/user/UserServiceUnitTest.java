package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.example.vex360.features.user.mapper.UserMapper;
import com.example.vex360.features.user.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.user.events.UserStatusChangedEvent;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.AuthProvider;
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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MailService mailService;

    private UserService userService;
    private UUID userId;
    private User sampleUser;

    @BeforeEach
    void setup() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        userService = new UserService(userRepository, passwordEncoder, userMapper, eventPublisher, mailService);
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
    void createUserGeneratesPasswordAndSendsCredentialsEmail() {
        CreateUserRequest request = CreateUserRequest.builder()
                .email("new@example.com")
                .fullName("New User")
                .phoneNumber("0912345678")
                .role(Role.ADMIN)
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals("encodedNewPassword", savedUser.getPassword());
        assertEquals("new@example.com", response.getEmail());

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        String generatedPassword = passwordCaptor.getValue();
        assertEquals(8, generatedPassword.length());
        assertTrue(generatedPassword.matches(".*[A-Z].*"));
        assertTrue(generatedPassword.matches(".*[a-z].*"));
        assertTrue(generatedPassword.matches(".*\\d.*"));
        assertTrue(generatedPassword.matches(".*[!@#$%^&*].*"));
        verify(mailService).sendNewUserCredentialsEmail("new@example.com", "New User", generatedPassword);
    }

    @Test
    void createUserThrowsWhenEmailExists() {
        CreateUserRequest request = CreateUserRequest.builder()
                .email("new@example.com")
                .fullName("New User")
                .phoneNumber("0912345678")
                .role(Role.ADMIN)
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> userService.createUser(request));

        assertSame(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
        verify(mailService, never()).sendNewUserCredentialsEmail(any(), any(), any());
    }

    @Test
    void createUser_Success_DefaultsActive() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("request@example.com")
                .password("Password123!")
                .fullName("Dto User")
                .phoneNumber("0987654321")
                .role(Role.VISITOR)
                .avatarUrl("avatar.png")
                .build();

        when(userRepository.existsByEmail("request@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(dto, UserStatus.ACTIVE);

        assertEquals("request@example.com", createdUser.getEmail());
        assertEquals(UserStatus.ACTIVE, createdUser.getStatus());
        assertEquals(Role.VISITOR, createdUser.getRole());
        verify(userRepository).save(createdUser);
    }

    @Test
    void createUser_WithStatusSuccess() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("request@example.com")
                .password("Password123!")
                .fullName("Dto User")
                .phoneNumber("0987654321")
                .role(Role.VISITOR)
                .avatarUrl("avatar.png")
                .build();

        when(userRepository.existsByEmail("request@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(dto, UserStatus.PENDING);

        assertEquals("request@example.com", createdUser.getEmail());
        assertEquals(UserStatus.PENDING, createdUser.getStatus());
        assertEquals(Role.VISITOR, createdUser.getRole());
        verify(userRepository).save(createdUser);
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

        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateCurrentUserProfile(sampleUser.getEmail(), request);

        assertEquals("New Name", sampleUser.getFullName());
        assertEquals("123", sampleUser.getPhoneNumber());
        assertEquals("new.png", sampleUser.getAvatarUrl());
        assertEquals("New Name", response.getFullName());
        assertEquals("123", response.getPhoneNumber());
    }

    @Test
    void updateCurrentUserProfileReturnsUpdatedPhoneNumber() {
        UpdateProfileRequest request = new UpdateProfileRequest(null, "0912345678", null);

        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateCurrentUserProfile(sampleUser.getEmail(), request);

        assertEquals("0912345678", sampleUser.getPhoneNumber());
        assertEquals("0912345678", response.getPhoneNumber());
    }

    @Test
    void changeCurrentUserPasswordThrowsWhenOldPasswordDoesNotMatch() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "NewPassword123!");

        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> userService.changeCurrentUserPassword(sampleUser.getEmail(), request));

        assertSame(ErrorCode.OLDPASSWORD_FAILED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeCurrentUserPasswordEncodesAndSavesNewPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("old", "NewPassword123!");

        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

        userService.changeCurrentUserPassword(sampleUser.getEmail(), request);

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void changeCurrentUserPasswordThrowsWhenProviderIsNotLocal() {
        User googleUser = User.builder()
                .id(UUID.randomUUID())
                .email("google@example.com")
                .provider(AuthProvider.GOOGLE)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest("old", "NewPassword123!");

        when(userRepository.findByEmail(googleUser.getEmail())).thenReturn(Optional.of(googleUser));

        AppException exception = assertThrows(AppException.class,
                () -> userService.changeCurrentUserPassword(googleUser.getEmail(), request));

        assertSame(ErrorCode.PROVIDER_NOT_SUPPORT_CHANGE_PASSWORD, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateRole_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO roleResponse = userService.updateRole(userId, Role.ADMIN);

        assertEquals("ADMIN", roleResponse.getRole());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void updateRole_ThrowsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.updateRole(userId, Role.ADMIN));
        assertSame(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateStatus_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO statusResponse = userService.updateStatus(userId, UserStatus.BLOCKED);

        assertEquals("BLOCKED", statusResponse.getStatus());
        verify(eventPublisher).publishEvent(any(UserStatusChangedEvent.class));
        verify(userRepository).save(sampleUser);
    }

    @Test
    void updateStatus_ThrowsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.updateStatus(userId, UserStatus.BLOCKED));
        assertSame(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUsersReturnsPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers("user", Role.VISITOR, UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(sampleUser), pageable, 1));

        PageResponse<UserResponseDTO> response = userService.getUsers(" user ", Role.VISITOR, UserStatus.ACTIVE,
                pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1, response.getTotalElements());
        verify(userRepository).searchUsers(eq("user"), eq(Role.VISITOR), eq(UserStatus.ACTIVE), eq(pageable));
    }

    // ==========================================
    // getUserById Tests
    // ==========================================

    @Test
    void getUserByIdReturnsUserResponseDTO() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        UserResponseDTO response = userService.getUserById(userId);

        assertEquals("user@example.com", response.getEmail());
        assertEquals("Old Name", response.getFullName());
    }

    // ==========================================
    // getCurrentUser Tests
    // ==========================================

    @Test
    void getCurrentUserReturnsUserResponseDTO() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        UserResponseDTO response = userService.getCurrentUser("user@example.com");

        assertEquals("user@example.com", response.getEmail());
        assertEquals("Old Name", response.getFullName());
    }

    @Test
    void getCurrentUserThrowsWhenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> userService.getCurrentUser("missing@example.com"));
        assertSame(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    // ==========================================
    // createUser(CreateUserRequest) edge cases
    // ==========================================

    @Test
    void createUser_RoleNull_DefaultsToVisitor() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("norole@example.com")
                .password("Password123!")
                .fullName("No Role User")
                .phoneNumber("0987654321")
                .role(null)
                .avatarUrl("avatar.png")
                .build();

        when(userRepository.existsByEmail("norole@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(dto, UserStatus.ACTIVE);

        assertEquals(Role.VISITOR, createdUser.getRole());
    }

    @Test
    void createUser_EmailExists_ThrowsEmailAlreadyExists() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("existing@example.com")
                .password("Password123!")
                .fullName("Existing User")
                .phoneNumber("0987654321")
                .role(Role.VISITOR)
                .avatarUrl("avatar.png")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(dto, UserStatus.ACTIVE));
        assertSame(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    // ==========================================
    // getUsers edge cases
    // ==========================================

    @Test
    void getUsersWithNullKeyword_PassesNullToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<UserResponseDTO> response = userService.getUsers(null, null, null, pageable);

        assertEquals(0, response.getContent().size());
        verify(userRepository).searchUsers(eq(null), eq(null), eq(null), eq(pageable));
    }

    // ==========================================
    // findOrCreateGoogleUser Tests
    // ==========================================

    @Test
    void findOrCreateGoogleUser_UserExists_ReturnsExistingUser() {
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(sampleUser));

        User result = userService.findOrCreateGoogleUser("google@example.com", "Google User", "avatar.png");

        assertSame(sampleUser, result);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findOrCreateGoogleUser_NewUser_WithFullName() {
        when(userRepository.findByEmail("new@google.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateGoogleUser("new@google.com", "New Google User", "avatar.png");

        assertEquals("new@google.com", result.getEmail());
        assertEquals("New Google User", result.getFullName());
        assertEquals(Role.VISITOR, result.getRole());
        assertEquals(AuthProvider.GOOGLE, result.getProvider());
        assertEquals("avatar.png", result.getAvatarUrl());
        assertNull(result.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findOrCreateGoogleUser_NewUser_FullNameNull_FallsBackToEmail() {
        when(userRepository.findByEmail("new@google.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateGoogleUser("new@google.com", null, "avatar.png");

        assertEquals("new@google.com", result.getFullName());
        verify(userRepository).save(any(User.class));
    }

    // ==========================================
    // incrementFailedAttempts Tests
    // ==========================================

    @Test
    void incrementFailedAttempts_BelowThreshold_IncrementsOnly() {
        sampleUser.setFailedLoginAttempts(2);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        userService.incrementFailedAttempts("user@example.com");

        assertEquals(3, sampleUser.getFailedLoginAttempts());
        assertNull(sampleUser.getLockoutEnd());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void incrementFailedAttempts_ReachesThreshold_SetsLockoutEnd() {
        sampleUser.setFailedLoginAttempts(4);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        userService.incrementFailedAttempts("user@example.com");

        assertEquals(5, sampleUser.getFailedLoginAttempts());
        assertNotNull(sampleUser.getLockoutEnd());
        assertTrue(sampleUser.getLockoutEnd().isAfter(Instant.now()));
        verify(userRepository).save(sampleUser);
    }

    @Test
    void incrementFailedAttempts_EmailNotFound_DoesNothing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.incrementFailedAttempts("missing@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    // ==========================================
    // resetFailedAttempts Tests
    // ==========================================

    @Test
    void resetFailedAttempts_Success() {
        sampleUser.setFailedLoginAttempts(3);
        sampleUser.setLockoutEnd(Instant.now().plusSeconds(900));
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        userService.resetFailedAttempts(sampleUser);

        assertEquals(0, sampleUser.getFailedLoginAttempts());
        assertNull(sampleUser.getLockoutEnd());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void resetFailedAttempts_UserNotFound_DoesNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.resetFailedAttempts(sampleUser));

        verify(userRepository, never()).save(any(User.class));
    }

    // ==========================================
    // updatePassword Tests
    // ==========================================

    @Test
    void updatePassword_EncodesAndSaves() {
        when(passwordEncoder.encode("newPassword")).thenReturn("encodedNewPassword");

        userService.updatePassword(sampleUser, "newPassword");

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void createUser_PreservesNullAvatarUrl() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("norole@example.com")
                .password("Password123!")
                .fullName("No Role User")
                .phoneNumber("0987654321")
                .role(Role.VISITOR)
                .avatarUrl(null)
                .build();

        when(userRepository.existsByEmail("norole@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(dto, UserStatus.ACTIVE);

        assertNull(createdUser.getAvatarUrl());
    }

    @Test
    void getUsersWithEmptyKeyword_PassesNullToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<UserResponseDTO> response = userService.getUsers("", null, null, pageable);

        assertEquals(0, response.getContent().size());
        verify(userRepository).searchUsers(eq(null), eq(null), eq(null), eq(pageable));
    }

    @Test
    void getUsersWithBlankKeyword_PassesNullToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<UserResponseDTO> response = userService.getUsers("   ", null, null, pageable);

        assertEquals(0, response.getContent().size());
        verify(userRepository).searchUsers(eq(null), eq(null), eq(null), eq(pageable));
    }

    @Test
    void getUserByEmail_Success() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        User result = userService.getUserByEmail("user@example.com");

        assertSame(sampleUser, result);
    }

    @Test
    void getUserByEmail_ThrowsWhenNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getUserByEmail("missing@example.com"));
        assertSame(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void findUserByEmail_Success() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.findUserByEmail("user@example.com");

        assertTrue(result.isPresent());
        assertSame(sampleUser, result.get());
    }

    @Test
    void findUserByEmail_NotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.findUserByEmail("missing@example.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void saveUserEntity_Success() {
        when(userRepository.save(sampleUser)).thenReturn(sampleUser);

        User result = userService.saveUserEntity(sampleUser);

        assertSame(sampleUser, result);
        verify(userRepository).save(sampleUser);
    }

    @Test
    void existsByEmail_True() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        boolean result = userService.existsByEmail("user@example.com");

        assertTrue(result);
    }

    @Test
    void existsByEmail_False() {
        when(userRepository.existsByEmail("missing@example.com")).thenReturn(false);

        boolean result = userService.existsByEmail("missing@example.com");

        assertTrue(!result);
    }

    @Test
    void createAdminUser_Success() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("adminPass")).thenReturn("encodedAdminPass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createAdminUser("admin@example.com", "adminPass", "Admin User");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("admin@example.com", savedUser.getEmail());
        assertEquals("encodedAdminPass", savedUser.getPassword());
        assertEquals("Admin User", savedUser.getFullName());
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
    }

    @Test
    void createAdminUser_ThrowsWhenEmailExists() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> userService.createAdminUser("admin@example.com", "adminPass", "Admin User"));
        assertSame(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserEntityById_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        User result = userService.getUserEntityById(userId);

        assertSame(sampleUser, result);
    }

    @Test
    void getUserEntityById_ThrowsWhenNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> userService.getUserEntityById(userId));
        assertSame(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createUser_StatusNull_DefaultsToActive() {
        CreateUserRequest dto = CreateUserRequest.builder()
                .email("nullstatus@example.com")
                .password("Password123!")
                .fullName("Null Status User")
                .phoneNumber("0987654321")
                .role(Role.VISITOR)
                .avatarUrl("avatar.png")
                .build();

        when(userRepository.existsByEmail("nullstatus@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(dto, null);

        assertEquals(UserStatus.ACTIVE, createdUser.getStatus());
    }
}
