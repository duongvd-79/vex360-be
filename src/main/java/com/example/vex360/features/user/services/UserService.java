package com.example.vex360.features.user.services;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.user.events.UserStatusChangedEvent;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.dtos.response.UserSummaryResponseDTO;
import com.example.vex360.features.user.mapper.UserMapper;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.LogSanitizer;
import com.example.vex360.shared.utils.RandomPasswordGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MailService mailService;

    @Transactional
    public UserResponseDTO createUser(CreateUserRequest request) {
        String generatedPassword = RandomPasswordGenerator.generate();
        User user = createAndSaveUser(
                request.getEmail(),
                generatedPassword,
                request.getFullName(),
                request.getPhoneNumber(),
                request.getRole(),
                request.getAvatarUrl(),
                UserStatus.ACTIVE);

        mailService.sendNewUserCredentialsEmail(user.getEmail(), user.getFullName(), generatedPassword);

        return userMapper.toUserResponseDTO(user);
    }

    @Transactional
    public User createUser(CreateUserRequest request, UserStatus status) {
        return createAndSaveUser(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhoneNumber(),
                request.getRole(),
                request.getAvatarUrl(),
                status);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponseDTO> getUsers(String keyword, Role role, UserStatus status, Pageable pageable) {
        Page<UserResponseDTO> users = userRepository.searchUsers(normalizeKeyword(keyword), role, status, pageable)
                .map(userMapper::toUserResponseDTO);
        return PageResponse.from(users);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponseDTO getUserSummary() {
        return new UserSummaryResponseDTO(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByRole(Role.ADMIN),
                userRepository.countByStatus(UserStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(UUID id) {
        return userMapper.toUserResponseDTO(getUserEntityById(id));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser(String email) {
        return userMapper.toUserResponseDTO(getUserByEmail(email));
    }

    @Transactional
    public UserResponseDTO updateCurrentUserProfile(String email, UpdateProfileRequest request) {
        User user = getUserByEmail(email);
        userMapper.updateProfile(user, request);
        return userMapper.toUserResponseDTO(userRepository.save(user));
    }

    @Transactional
    public void changeCurrentUserPassword(String email, ChangePasswordRequest request) {
        User user = getUserByEmail(email);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLDPASSWORD_FAILED);
        }
        updatePassword(user, request.getNewPassword());
    }

    @Transactional
    public UserResponseDTO updateRole(UUID id, Role role) {
        User user = getUserEntityById(id);
        user.setRole(role);
        return userMapper.toUserResponseDTO(userRepository.save(user));
    }

    @Transactional
    public UserResponseDTO updateStatus(UUID id, UserStatus status) {
        User user = getUserEntityById(id);
        user.setStatus(status);
        eventPublisher.publishEvent(new UserStatusChangedEvent(this, user));

        return userMapper.toUserResponseDTO(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Tìm user theo email hoặc tạo mới nếu chưa tồn tại (dành cho OAuth2 Google).
     * Nếu email đã tồn tại (đăng ký LOCAL), vẫn cho phép đăng nhập bình thường.
     */
    @Transactional
    public User findOrCreateGoogleUser(String email, String fullName, String avatarUrl) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .email(email)
                    .password(null)
                    .fullName(fullName != null ? fullName : email)
                    .role(Role.VISITOR)
                    .provider(AuthProvider.GOOGLE)
                    .avatarUrl(avatarUrl)
                    .build();
            return userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);

    }

    @Transactional
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public User saveUserEntity(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public void createAdminUser(String email, String password, String fullName) {
        createAndSaveUser(email, password, fullName, null, Role.ADMIN, null, UserStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private User createAndSaveUser(
            String email,
            String password,
            String fullName,
            String phoneNumber,
            Role role,
            String avatarUrl,
            UserStatus status) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .role(role != null ? role : Role.VISITOR)
                .avatarUrl(avatarUrl)
                .status(status != null ? status : UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    @Transactional
    public void incrementFailedAttempts(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);
            if (newAttempts >= 5) {
                user.setLockoutEnd(Instant.now().plusSeconds(900)); // khóa 15 phút
                log.warn("User account locked due to too many failed attempts: {}", LogSanitizer.sanitize(email));
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void resetFailedAttempts(User user) {
        userRepository.findById(user.getId()).ifPresent(u -> {
            u.setFailedLoginAttempts(0);
            u.setLockoutEnd(null);
            userRepository.save(u);
        });
    }
}
