package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
        UpdateCompanyProfileRequest request = UpdateCompanyProfileRequest.builder()
                .name("Company A")
                .industry("Technology")
                .description("Company description")
                .logoUrl("https://cdn.example.com/logo.png")
                .website("https://example.com")
                .phone("0912345678")
                .address("123 Main St")
                .build();

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("Technology", response.getIndustry());
        assertEquals("ACTIVE", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void getCurrentUserCompanyReturnsNullWhenMissing() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());

        CompanyResponseDTO response = companyService.getCurrentUserCompany(owner);

        org.junit.jupiter.api.Assertions.assertNull(response);
    }

    @Test
    void updateCurrentUserCompany_IncompleteProfile_StatusUnchanged() {
        UpdateCompanyProfileRequest request = UpdateCompanyProfileRequest.builder()
                .industry("Technology")
                .description("Company description")
                .logoUrl("https://cdn.example.com/logo.png")
                .website("https://example.com")
                .phone("0912345678")
                .address(null) // missing address → incomplete
                .build();

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("INCOMPLETE_PROFILE", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void updateCurrentUserCompany_ArchivedCompany_StatusStaysArchived() {
        company.setStatus(CompanyStatus.ARCHIVED);

        UpdateCompanyProfileRequest request = UpdateCompanyProfileRequest.builder()
                .name("Company A")
                .industry("Technology")
                .description("Company description")
                .logoUrl("https://cdn.example.com/logo.png")
                .website("https://example.com")
                .phone("0912345678")
                .address("123 Main St")
                .build();

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("ARCHIVED", response.getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void updateCurrentUserCompany_CompanyNotFound_CreatesDefaultCompany() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());

        UpdateCompanyProfileRequest request = UpdateCompanyProfileRequest.builder()
                .name("New Company")
                .industry("Technology")
                .description("Desc")
                .logoUrl("https://cdn.example.com/logo.png")
                .website("https://example.com")
                .phone("0912345678")
                .address("123 Main St")
                .build();

        Company created = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(owner)
                .name("New Company")
                .email("owner@example.com")
                .status(CompanyStatus.INCOMPLETE_PROFILE)
                .build();

        when(companyRepository.findByOwnerUserIdForUpdate(owner.getId())).thenReturn(Optional.of(created));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("New Company", response.getName());
        assertEquals("ACTIVE", response.getStatus());
        verify(companyRepository).insertCompanyIfAbsent(anyString(), eq(owner.getId().toString()), eq("New Company"), eq(owner.getEmail()), eq("0912345678"), eq("INCOMPLETE_PROFILE"));
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
    void ensureCompanyForCompanyRole_ExistingCompany_ReturnsWithoutOverwrite() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));

        Optional<Company> result = companyService.ensureCompanyForCompanyRole(
                owner, "Tên mới", "new@example.com", "0999999999");

        assertTrue(result.isPresent());
        assertSame(company, result.get());
        assertEquals("Company A", company.getName());
        assertEquals("owner@example.com", company.getEmail());
        verify(companyRepository, never()).insertCompanyIfAbsent(
                anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void ensureCompanyForCompanyRole_NonCompanyRole_IsAbsoluteNoOp() {
        owner.setRole(Role.VISITOR);

        Optional<Company> result = companyService.ensureCompanyForCompanyRole(
                owner, "Không được tạo", "visitor@example.com", "0900000000");

        assertTrue(result.isEmpty());
        verifyNoInteractions(companyRepository);
    }

    @Test
    void ensureCompanyForCompanyRole_MissingCompany_CreatesWithPreferredValues() {
        Company created = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(owner)
                .name("Công ty Mộc Việt")
                .email("contact@mocviet.vn")
                .phone("0912345678")
                .status(CompanyStatus.INCOMPLETE_PROFILE)
                .build();
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());
        when(companyRepository.findByOwnerUserIdForUpdate(owner.getId())).thenReturn(Optional.of(created));

        Optional<Company> result = companyService.ensureCompanyForCompanyRole(
                owner, "  Công ty Mộc Việt  ", " contact@mocviet.vn ", " 0912345678 ");

        assertSame(created, result.orElseThrow());
        verify(companyRepository).insertCompanyIfAbsent(
                anyString(),
                eq(owner.getId().toString()),
                eq("Công ty Mộc Việt"),
                eq("contact@mocviet.vn"),
                eq("0912345678"),
                eq(CompanyStatus.INCOMPLETE_PROFILE.name()));
    }

    @Test
    void ensureCompanyForCompanyRole_BlankPreferredValues_UsesUserFallbacks() {
        owner.setFullName("Nguyễn Văn An");
        owner.setPhoneNumber("0987654321");
        Company created = Company.builder().id(UUID.randomUUID()).ownerUser(owner).build();
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());
        when(companyRepository.findByOwnerUserIdForUpdate(owner.getId())).thenReturn(Optional.of(created));

        companyService.ensureCompanyForCompanyRole(owner, " ", null, "");

        verify(companyRepository).insertCompanyIfAbsent(
                anyString(),
                eq(owner.getId().toString()),
                eq("Nguyễn Văn An"),
                eq("owner@example.com"),
                eq("0987654321"),
                eq(CompanyStatus.INCOMPLETE_PROFILE.name()));
    }

    @Test
    void ensureCompanyForCompanyRole_ConcurrentInsertLosesRace_ReturnsWinner() {
        Company winner = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(owner)
                .name("Company from other request")
                .status(CompanyStatus.INCOMPLETE_PROFILE)
                .build();
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());
        when(companyRepository.insertCompanyIfAbsent(
                anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(0);
        when(companyRepository.findByOwnerUserIdForUpdate(owner.getId())).thenReturn(Optional.of(winner));

        Optional<Company> result = companyService.ensureCompanyForCompanyRole(
                owner, "Losing request", null, null);

        assertSame(winner, result.orElseThrow());
    }

    @Test
    void ensureCompanyForCompanyRole_NullOwner_ThrowsUnauthenticatedWithoutRepositoryAccess() {
        AppException exception = assertThrows(AppException.class,
                () -> companyService.ensureCompanyForCompanyRole(null, null, null, null));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verifyNoInteractions(companyRepository);
    }

    @Test
    void updateCurrentUserCompany_PartialUpdate_OnlyUpdatesProvidedFields() {
        company.setIndustry("Old Industry");
        company.setDescription("Old Description");

        UpdateCompanyProfileRequest request = UpdateCompanyProfileRequest.builder()
                .industry("New Industry")
                .build();

        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponseDTO response = companyService.updateCurrentUserCompany(owner, request);

        assertEquals("New Industry", response.getIndustry());
        assertEquals("INCOMPLETE_PROFILE", response.getStatus());
    }

    // ================= getCompanyEntityForCurrentUser =================

    @Test
    void getCompanyEntityForCurrentUser_Success_ReturnsCompanyEntity() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.of(company));

        Company result = companyService.getCompanyEntityForCurrentUser(owner);

        assertSame(company, result);
    }

    @Test
    void getCompanyEntityForCurrentUser_CurrentUserNull_ThrowsUnauthenticated() {
        AppException exception = assertThrows(AppException.class,
                () -> companyService.getCompanyEntityForCurrentUser(null));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void getCompanyEntityForCurrentUser_CompanyNotFound_ThrowsCompanyNotFound() {
        when(companyRepository.findByOwnerUserId(owner.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> companyService.getCompanyEntityForCurrentUser(owner));

        assertSame(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    // ================= existsByOwnerUserId =================

    @Test
    void existsByOwnerUserId_OwnerHasCompany_ReturnsTrue() {
        UUID ownerId = UUID.randomUUID();
        when(companyRepository.existsByOwnerUserId(ownerId)).thenReturn(true);

        boolean result = companyService.existsByOwnerUserId(ownerId);

        assertTrue(result);
    }

    @Test
    void existsByOwnerUserId_OwnerHasNoCompany_ReturnsFalse() {
        UUID ownerId = UUID.randomUUID();
        when(companyRepository.existsByOwnerUserId(ownerId)).thenReturn(false);

        boolean result = companyService.existsByOwnerUserId(ownerId);

        assertFalse(result);
    }
}
