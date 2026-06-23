package com.example.vex360.features.company.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.shared.entities.Company;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CompanyMapper {
    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    @Mapping(target = "status", expression = "java(company.getStatus().name())")
    CompanyResponseDTO toResponse(Company company);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "industry", source = "industry")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "logoUrl", source = "logoUrl")
    @Mapping(target = "website", source = "website")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "address", source = "address")
    void updateProfile(@MappingTarget Company company, UpdateCompanyProfileRequest request);
}
