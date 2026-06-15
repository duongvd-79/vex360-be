package com.example.vex360.features.user;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.stereotype.Component;

import com.example.vex360.features.user.dtos.UserRequestDTO;
import com.example.vex360.features.user.dtos.UserResponseDTO;
import com.example.vex360.shared.entities.User;

@Component
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    User toUser(UserRequestDTO userRequestDTO);

    UserResponseDTO toUserResponseDTO(User user);

    List<UserResponseDTO> toUserResponseDTOs(List<User> users);
}
