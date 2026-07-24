package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CompanyStorageServiceUnitTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyStorageService companyStorageService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Test Company")
                .storageUsedBytes(100L)
                .storageReservedBytes(50L)
                .storageQuotaBytes(500L)
                .build();
    }

    @Test
    void testCheckQuota_Exceeded_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            companyStorageService.checkQuota(company, 351L);
        });
        assertEquals(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void testCheckQuota_WithinLimit_Success() {
        companyStorageService.checkQuota(company, 350L);
        // Should not throw exception
    }

    @Test
    void testAddUsage_Success() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.addUsage(company, 50L);

        assertEquals(150L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void reserveUsage_IncreasesReservedWithoutChargingUsed() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.reserveUsage(company, 40L);

        assertEquals(100L, company.getStorageUsedBytes());
        assertEquals(90L, company.getStorageReservedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void reserveUsage_AcceptsExactBoundaryThenRejectsOneMoreByte() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.reserveUsage(company, 350L);
        AppException exception = assertThrows(
                AppException.class,
                () -> companyStorageService.reserveUsage(company, 1L));

        assertEquals(400L, company.getStorageReservedBytes());
        assertEquals(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void promoteReservedUsage_MovesBytesFromReservedToUsed() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.promoteReservedUsage(company, 30L);

        assertEquals(130L, company.getStorageUsedBytes());
        assertEquals(20L, company.getStorageReservedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void releaseReservedUsage_CannotReleaseMoreThanReserved() {
        stubCompanyLock();
        AppException exception = assertThrows(
                AppException.class,
                () -> companyStorageService.releaseReservedUsage(company, 51L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    @Test
    void testDeductUsage_Success() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.deductUsage(company, 30L);

        assertEquals(70L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void testDeductUsage_NegativeResult_ClampedToZero() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.deductUsage(company, 150L);

        assertEquals(0L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    private void stubCompanyLock() {
        when(companyRepository.findByIdForUpdate(company.getId())).thenReturn(Optional.of(company));
    }
}
