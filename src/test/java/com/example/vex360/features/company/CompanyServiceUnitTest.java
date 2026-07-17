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
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
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

    @Test
    void updateCurrentUserCompany_IncompleteProfile_StatusUnchanged() {
        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "Technology",
                "Company description",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "0912345678",
                null); // missing address → incomplete

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("INCOMPLETE_PROFILE", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void updateCurrentUserCompany_ArchivedCompany_StatusStaysArchived() {
        company.setStatus(CompanyStatus.ARCHIVED);

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

        assertEquals("ARCHIVED", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void updateCurrentUserCompany_CompanyNotFound_ThrowsCompanyNotFound() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());

        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "Technology", "Desc", "logo.png", "https://example.com", "0912345678", "123 Main St");

        AppException exception = assertThrows(AppException.class,
                () -> companyService.updateCurrentUserCompany(owner, request));

        assertSame(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getCurrentUserCompany_CurrentUserNull_ThrowsUnauthenticated() {
        AppException exception = assertThrows(AppException.class,
                () -> companyService.getCurrentUserCompany(null));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void getCurrentUserCompany_CurrentUserIdNull_ThrowsUnauthenticated() {
        User userWithNullId = User.builder().id(null).email("test@example.com").build();

        AppException exception = assertThrows(AppException.class,
                () -> companyService.getCurrentUserCompany(userWithNullId));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void createCompany_Success_WithIncompleteProfileStatus() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company result = companyService.createCompany(owner, "New Company", "contact@company.com", "0912345678");

        assertEquals("New Company", result.getName());
        assertEquals("contact@company.com", result.getEmail());
        assertEquals("0912345678", result.getPhone());
        assertEquals(CompanyStatus.INCOMPLETE_PROFILE, result.getStatus());
        assertSame(owner, result.getOwnerUser());
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    void updateCurrentUserCompany_PartialUpdate_OnlyUpdatesProvidedFields() {
        company.setIndustry("Old Industry");
        company.setDescription("Old Description");

        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "New Industry",
                null,
                null,
                null,
                null,
                null);

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("New Industry", response.getIndustry());
        assertEquals("INCOMPLETE_PROFILE", response.getStatus());
    }
}
