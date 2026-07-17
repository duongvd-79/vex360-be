package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.security.CompanyProfileAccessGuard;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CompanyProfileAccessGuardUnitTest {
    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyProfileAccessGuard guard;

    @Test
    void activeCompanyIsAllowed() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Company company = Company.builder().status(CompanyStatus.ACTIVE).build();
        when(companyRepository.findByOwnerUserId(userId)).thenReturn(Optional.of(company));

        assertSame(company, guard.requireActiveCompany(user));
    }

    @Test
    void incompleteCompanyIsRejected() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Company company = Company.builder().status(CompanyStatus.INCOMPLETE_PROFILE).build();
        when(companyRepository.findByOwnerUserId(userId)).thenReturn(Optional.of(company));

        AppException exception = assertThrows(AppException.class,
                () -> guard.requireActiveCompany(user));

        assertEquals(ErrorCode.COMPANY_PROFILE_INCOMPLETE, exception.getErrorCode());
    }

    @Test
    void archivedCompanyIsRejected() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Company company = Company.builder().status(CompanyStatus.ARCHIVED).build();
        when(companyRepository.findByOwnerUserId(userId)).thenReturn(Optional.of(company));

        AppException exception = assertThrows(AppException.class,
                () -> guard.requireActiveCompany(user));

        assertEquals(ErrorCode.COMPANY_ARCHIVED, exception.getErrorCode());
    }

    @Test
    void missingCompanyIsRejected() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(companyRepository.findByOwnerUserId(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> guard.requireActiveCompany(user));

        assertEquals(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void unauthenticatedUserIsRejected() {
        AppException exception = assertThrows(AppException.class,
                () -> guard.requireActiveCompany(null));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
