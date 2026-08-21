package com.example.vex360.features.partnership.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.entities.PartnershipRequest;

@Mapper(componentModel = "spring")
public interface PartnershipRequestMapper {
    @Mapping(target = "submittedByUserId", source = "submittedByUser.id")
    @Mapping(target = "submittedByUserEmail", source = "submittedByUser.email")
    @Mapping(target = "approvedUserId", source = "approvedUser.id")
    @Mapping(target = "accountAction", expression = "java(request.getAccountAction() != null ? request.getAccountAction().name() : null)")
    @Mapping(target = "requestedRole", expression = "java(request.getRequestedRole() != null ? request.getRequestedRole().name() : null)")
    @Mapping(target = "status", expression = "java(request.getStatus() != null ? request.getStatus().name() : null)")
    PartnershipRequestResponseDTO toResponse(PartnershipRequest request);
}
