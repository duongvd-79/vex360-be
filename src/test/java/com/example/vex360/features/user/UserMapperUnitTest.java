package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.mapper.UserMapper;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

class UserMapperUnitTest {

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toUserResponseDTOIncludesCreatedAtAndUpdatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 29, 9, 15);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 6, 29, 10, 30);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("User Name")
                .phoneNumber("0912345678")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        ReflectionTestUtils.setField(user, "updatedAt", updatedAt);

        UserResponseDTO response = userMapper.toUserResponseDTO(user);

        assertEquals(createdAt, ReflectionTestUtils.getField(response, "createdAt"));
        assertEquals(updatedAt, ReflectionTestUtils.getField(response, "updatedAt"));
    }
}
