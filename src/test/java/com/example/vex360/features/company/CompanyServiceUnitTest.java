package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.features.company.mapper.CompanyMapper;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CompanyServiceUnitTest {
    @Mock
    private CompanyRepository companyRepository;

    private CompanyService companyService;
    private User owner;
    private Company company;

    @BeforeEach
    void setup() {
        CompanyMapper companyMapper = Mappers.getMapper(CompanyMapper.class);
        companyService = new CompanyService(companyRepository, companyMapper);
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .role(Role.EXHIBITOR)
                .status(UserStatus.ACTIVE)
                .build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(owner)
                .name("Company A")
                .email("owner@example.com")
                .status(CompanyStatus.INCOMPLETE_PROFILE)
                .build();
    }

    @Test
    void getCurrentUserCompanyReturnsCompany() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));

        CompanyResponseDTO response = companyService.getCurrentUserCompany(owner);

        assertEquals(company.getId(), response.getId());
        assertEquals(owner.getId(), response.getOwnerUserId());
        assertEquals("INCOMPLETE_PROFILE", response.getStatus());
    }

    @Test
    void updateCurrentUserCompanyActivatesWhenProfileIsComplete() {
        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "Technology",
                "Company description",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "0912345678",
                "123 Main St");

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("Technology", response.getIndustry());
        assertEquals("ACTIVE", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void getCurrentUserCompanyThrowsWhenMissing() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> companyService.getCurrentUserCompany(owner));

        assertSame(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }
}
