package com.example.vex360.features.partnership.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.shared.entities.PartnershipRequest;

@Mapper(componentModel = "spring")
public interface PartnershipRequestMapper {
    @Mapping(target = "submittedByUserId", source = "submittedByUser.id")
    @Mapping(target = "submittedByUserEmail", source = "submittedByUser.email")
    @Mapping(target = "requestedRole", expression = "java(request.getRequestedRole().name())")
    @Mapping(target = "status", expression = "java(request.getStatus().name())")
    PartnershipRequestResponseDTO toResponse(PartnershipRequest request);
}
