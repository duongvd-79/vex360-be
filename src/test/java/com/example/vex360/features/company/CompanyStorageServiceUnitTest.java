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

import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
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

    @Test
    void reconcileUsage_ReleasesOldPanoramaBeforeAddingReplacement() {
        company.setStorageUsedBytes(480L);
        company.setStorageReservedBytes(0L);
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.reconcileUsage(company, 100L, 110L, 0L);

        assertEquals(490L, company.getStorageUsedBytes());
        assertEquals(0L, company.getStorageReservedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void reconcileUsage_LeavesCountersUntouchedWhenProjectedUsageExceedsQuota() {
        company.setStorageUsedBytes(480L);
        company.setStorageReservedBytes(0L);
        stubCompanyLock();

        AppException exception = assertThrows(
                AppException.class,
                () -> companyStorageService.reconcileUsage(company, 0L, 30L, 0L));

        assertEquals(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
        assertEquals(480L, company.getStorageUsedBytes());
        assertEquals(0L, company.getStorageReservedBytes());
    }

    private void stubCompanyLock() {
        when(companyRepository.findByIdForUpdate(company.getId())).thenReturn(Optional.of(company));
    }

    // ================= checkQuota =================

    @Test
    void checkQuota_NegativeBytes_ThrowsInvalidStorageUsage() {
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.checkQuota(company, -1L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    // ================= addUsage =================

    @Test
    void addUsage_CompanyNotFound_ThrowsCompanyNotFound() {
        when(companyRepository.findByIdForUpdate(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.addUsage(company, 50L));

        assertEquals(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    // ================= deductUsage =================

    @Test
    void deductUsage_NegativeBytes_ThrowsInvalidStorageUsage() {
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.deductUsage(company, -1L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    // ================= promoteReservedUsage =================

    @Test
    void promoteReservedUsage_InsufficientReserved_ThrowsInvalidStorageUsage() {
        stubCompanyLock();

        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.promoteReservedUsage(company, 51L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    @Test
    void promoteReservedUsage_NegativeBytes_ThrowsInvalidStorageUsage() {
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.promoteReservedUsage(company, -1L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    // ================= releaseReservedUsage =================

    @Test
    void releaseReservedUsage_Success_DecreasesReservedOnly() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        companyStorageService.releaseReservedUsage(company, 20L);

        assertEquals(30L, company.getStorageReservedBytes());
        assertEquals(100L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void releaseReservedUsage_NegativeBytes_ThrowsInvalidStorageUsage() {
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.releaseReservedUsage(company, -1L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    // ================= adjustReservation =================

    @Test
    void adjustReservation_Success_UpdatesReservedToActualBytes() {
        stubCompanyLock();
        when(companyRepository.save(company)).thenReturn(company);

        // reserved=50 -> release 30 of the old estimate, commit 25 as the real size
        companyStorageService.adjustReservation(company, 30L, 25L);

        assertEquals(45L, company.getStorageReservedBytes());
        assertEquals(100L, company.getStorageUsedBytes());
        verify(companyRepository).save(company);
    }

    @Test
    void adjustReservation_PreviousBytesExceedsReserved_ThrowsInvalidStorageUsage() {
        stubCompanyLock();

        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.adjustReservation(company, 51L, 10L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    @Test
    void adjustReservation_QuotaExceeded_ThrowsStorageQuotaExceeded() {
        stubCompanyLock();

        // used=100, reserved=50, quota=500 -> releasing all 50 then committing 401 overflows quota
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.adjustReservation(company, 50L, 401L));

        assertEquals(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void adjustReservation_NegativeBytes_ThrowsInvalidStorageUsage() {
        AppException exception = assertThrows(AppException.class,
                () -> companyStorageService.adjustReservation(company, -1L, 10L));

        assertEquals(ErrorCode.INVALID_STORAGE_USAGE, exception.getErrorCode());
    }

    // ================= getUsage =================

    @Test
    void getUsage_NormalCase_ReturnsCorrectPercentageAndAvailable() {
        StorageUsageResponseDTO result = companyStorageService.getUsage(company);

        assertEquals(100L, result.getUsedBytes());
        assertEquals(50L, result.getReservedBytes());
        assertEquals(500L, result.getQuotaBytes());
        assertEquals(350L, result.getAvailableBytes());
        assertEquals(20.0, result.getUsedPercentage());
    }

    @Test
    void getUsage_ZeroQuota_PercentageIsZeroNoDivisionByZero() {
        Company zeroQuotaCompany = Company.builder()
                .id(UUID.randomUUID())
                .storageUsedBytes(10L)
                .storageReservedBytes(0L)
                .storageQuotaBytes(0L)
                .build();

        StorageUsageResponseDTO result = companyStorageService.getUsage(zeroQuotaCompany);

        assertEquals(0.0, result.getUsedPercentage());
        assertEquals(0L, result.getAvailableBytes());
    }

    @Test
    void getUsage_UsedPlusReservedExceedsQuota_AvailableBytesClampedToZero() {
        Company overCompany = Company.builder()
                .id(UUID.randomUUID())
                .storageUsedBytes(450L)
                .storageReservedBytes(100L)
                .storageQuotaBytes(500L)
                .build();

        StorageUsageResponseDTO result = companyStorageService.getUsage(overCompany);

        assertEquals(0L, result.getAvailableBytes());
    }

    @Test
    void getUsage_SplitsMediaAssetBytesFromProductBytes() {
        // usedBytes=100 gồm cả media lẫn sản phẩm; media chiếm 60 -> sản phẩm còn 40.
        StorageUsageResponseDTO result = companyStorageService.getUsage(company, 60L);

        assertEquals(100L, result.getUsedBytes());
        assertEquals(60L, result.getMediaAssetUsedBytes());
        assertEquals(40L, result.getProductUsedBytes());
    }

    @Test
    void getUsage_MediaAssetBytesExceedUsedBytes_ProductUsedBytesClampedToZero() {
        // Dữ liệu media và usedBytes được truy vấn độc lập nhau nên có thể lệch pha;
        // productUsedBytes không được phép âm.
        StorageUsageResponseDTO result = companyStorageService.getUsage(company, 150L);

        assertEquals(150L, result.getMediaAssetUsedBytes());
        assertEquals(0L, result.getProductUsedBytes());
    }

    @Test
    void getUsage_CompanyIdNull_ReturnsUsageWithoutMediaBreakdown() {
        Company companyWithoutId = Company.builder()
                .storageUsedBytes(100L)
                .storageReservedBytes(0L)
                .storageQuotaBytes(500L)
                .build();

        StorageUsageResponseDTO result = companyStorageService.getUsage(companyWithoutId);

        assertEquals(0L, result.getMediaAssetUsedBytes());
        assertEquals(100L, result.getProductUsedBytes());
    }
}
