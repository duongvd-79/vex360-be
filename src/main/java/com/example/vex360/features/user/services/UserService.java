package com.example.vex360.features.user.services;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.mapper.UserMapper;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserResponseDTO createUser(CreateUserRequest request) {
        User user = createAndSaveUser(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhoneNumber(),
                request.getRole() == null ? Role.VISITOR : request.getRole(),
                request.getAvatarUrl());

        return userMapper.toUserResponseDTO(user);
    }

    @Transactional
    public User createUser(UserRequestDTO request) {
        Role userRole = parseRoleOrDefault(request.getRole());
        return createAndSaveUser(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhoneNumber(),
                userRole,
                request.getAvatarUrl());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponseDTO> getUsers(Pageable pageable) {
        Page<UserResponseDTO> users = userRepository.findAll(pageable)
                .map(userMapper::toUserResponseDTO);
        return PageResponse.from(users);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(UUID id) {
        return userMapper.toUserResponseDTO(getUserEntityById(id));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser(User currentUser) {
        return userMapper.toUserResponseDTO(getUserEntityById(currentUser.getId()));
    }

    @Transactional
    public UserResponseDTO updateCurrentUserProfile(User currentUser, UpdateProfileRequest request) {
        User user = getUserEntityById(currentUser.getId());
        userMapper.updateProfile(user, request);
        return userMapper.toUserResponseDTO(userRepository.save(user));
    }

    @Transactional
    public void changeCurrentUserPassword(User currentUser, ChangePasswordRequest request) {
        User user = getUserEntityById(currentUser.getId());
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
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
        // refreshTokenRepository.findAllByUser(user).forEach(refreshToken -> {
        //     log.info("Found refresh token: {}", refreshToken.getToken());
        //     refreshTokenRepository.delete(refreshToken);
        // });
        refreshTokenRepository.deleteByUser(user);
        return userMapper.toUserResponseDTO(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User getUserEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private User createAndSaveUser(
            String email,
            String password,
            String fullName,
            String phoneNumber,
            Role role,
            String avatarUrl) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .role(role)
                .avatarUrl(avatarUrl)
                .build();

        return userRepository.save(user);
    }

    private Role parseRoleOrDefault(String role) {
        if (role == null || role.isBlank()) {
            return Role.VISITOR;
        }

        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }
    }
}
