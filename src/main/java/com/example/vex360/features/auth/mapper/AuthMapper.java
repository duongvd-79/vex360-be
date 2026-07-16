package com.example.vex360.features.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.stereotype.Component;

import com.example.vex360.features.auth.dtos.request.RegisterRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;

@Component
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "role", constant = "VISITOR")
    CreateUserRequest toCreateUserRequest(RegisterRequest registerRequest);
}
