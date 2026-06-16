package com.example.vex360.features.user.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.shared.entities.User;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
    UserResponseDTO toUserResponseDTO(User user);

    List<UserResponseDTO> toUserResponseDTOs(List<User> users);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    void updateProfile(@MappingTarget User user, UpdateProfileRequest request);
}
