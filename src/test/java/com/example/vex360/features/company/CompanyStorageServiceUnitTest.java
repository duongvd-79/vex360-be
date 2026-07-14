package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .storageQuotaBytes(500L)
                .build();
    }

    @Test
    void testCheckQuota_Exceeded_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            companyStorageService.checkQuota(company, 401L);
        });
        assertEquals(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void testCheckQuota_WithinLimit_Success() {
        companyStorageService.checkQuota(company, 400L);
        // Should not throw exception
    }

    @Test
    void testAddUsage_Success() {
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.addUsage(company, 50L);

        assertEquals(150L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void testDeductUsage_Success() {
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.deductUsage(company, 30L);

        assertEquals(70L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void testDeductUsage_NegativeResult_ClampedToZero() {
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.deductUsage(company, 150L);

        assertEquals(0L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }
}
