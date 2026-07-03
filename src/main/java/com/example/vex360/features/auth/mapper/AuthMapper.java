package com.example.vex360.features.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.stereotype.Component;

import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;

@Component
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "role", ignore = true)
    UserRequestDTO toUserRequestDTO(RegisterRequest registerRequest);
}