package com.example.vex360.features.company.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyStorageService {

    private final CompanyRepository companyRepository;

    public void checkQuota(Company company, long fileSizeBytes) {
        validateBytes(fileSizeBytes);
        if (used(company) + reserved(company) + fileSizeBytes > quota(company)) {
            throw new AppException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
    }

    @Transactional
    public void addUsage(Company company, long fileSizeBytes) {
        Company locked = lock(company);
        checkQuota(locked, fileSizeBytes);
        locked.setStorageUsedBytes(used(locked) + fileSizeBytes);
        companyRepository.save(locked);
        log.info("Storage usage updated for company {}: +{}B, total={}B",
                locked.getId(), fileSizeBytes, locked.getStorageUsedBytes());
    }

    @Transactional
    public void deductUsage(Company company, long fileSizeBytes) {
        validateBytes(fileSizeBytes);
        Company locked = lock(company);
        long newUsed = Math.max(0, used(locked) - fileSizeBytes);
        locked.setStorageUsedBytes(newUsed);
        companyRepository.save(locked);
        log.info("Storage usage updated for company {}: -{}B, total={}B",
                locked.getId(), fileSizeBytes, newUsed);
    }

    @Transactional
    public void reserveUsage(Company company, long fileSizeBytes) {
        Company locked = lock(company);
        checkQuota(locked, fileSizeBytes);
        locked.setStorageReservedBytes(reserved(locked) + fileSizeBytes);
        companyRepository.save(locked);
    }

    @Transactional
    public void adjustReservation(Company company, long previousBytes, long actualBytes) {
        validateBytes(previousBytes);
        validateBytes(actualBytes);
        Company locked = lock(company);
        if (reserved(locked) < previousBytes) {
            throw new AppException(ErrorCode.INVALID_STORAGE_USAGE);
        }
        long remainingReserved = reserved(locked) - previousBytes;
        if (used(locked) + remainingReserved + actualBytes > quota(locked)) {
            throw new AppException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
        locked.setStorageReservedBytes(remainingReserved + actualBytes);
        companyRepository.save(locked);
    }

    @Transactional
    public void promoteReservedUsage(Company company, long fileSizeBytes) {
        validateBytes(fileSizeBytes);
        Company locked = lock(company);
        if (reserved(locked) < fileSizeBytes) {
            throw new AppException(ErrorCode.INVALID_STORAGE_USAGE);
        }
        locked.setStorageReservedBytes(reserved(locked) - fileSizeBytes);
        locked.setStorageUsedBytes(used(locked) + fileSizeBytes);
        companyRepository.save(locked);
    }

    @Transactional
    public void releaseReservedUsage(Company company, long fileSizeBytes) {
        validateBytes(fileSizeBytes);
        Company locked = lock(company);
        if (reserved(locked) < fileSizeBytes) {
            throw new AppException(ErrorCode.INVALID_STORAGE_USAGE);
        }
        locked.setStorageReservedBytes(reserved(locked) - fileSizeBytes);
        companyRepository.save(locked);
    }

    @Transactional
    public void reconcileUsage(
            Company company,
            long releasedUsedBytes,
            long addedUsedBytes,
            long promotedReservedBytes) {
        validateBytes(releasedUsedBytes);
        validateBytes(addedUsedBytes);
        validateBytes(promotedReservedBytes);
        Company locked = lock(company);
        if (reserved(locked) < promotedReservedBytes) {
            throw new AppException(ErrorCode.INVALID_STORAGE_USAGE);
        }
        long newReserved = reserved(locked) - promotedReservedBytes;
        long newUsed = Math.max(0, used(locked) - releasedUsedBytes)
                + addedUsedBytes
                + promotedReservedBytes;
        if (newUsed + newReserved > quota(locked)) {
            throw new AppException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
        locked.setStorageReservedBytes(newReserved);
        locked.setStorageUsedBytes(newUsed);
        companyRepository.save(locked);
    }

    public StorageUsageResponseDTO getUsage(Company company) {
        long usedBytes = used(company);
        long reservedBytes = reserved(company);
        long quotaBytes = quota(company);
        double percentage = quotaBytes > 0 ? (double) usedBytes / quotaBytes * 100 : 0;
        return StorageUsageResponseDTO.builder()
                .usedBytes(usedBytes)
                .reservedBytes(reservedBytes)
                .quotaBytes(quotaBytes)
                .availableBytes(Math.max(0, quotaBytes - usedBytes - reservedBytes))
                .usedPercentage(Math.round(percentage * 10.0) / 10.0)
                .build();
    }

    private Company lock(Company company) {
        if (company == null || company.getId() == null) {
            throw new AppException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return companyRepository.findByIdForUpdate(company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void validateBytes(long bytes) {
        if (bytes < 0) {
            throw new AppException(ErrorCode.INVALID_STORAGE_USAGE);
        }
    }

    private long used(Company company) {
        return company.getStorageUsedBytes() == null ? 0 : company.getStorageUsedBytes();
    }

    private long reserved(Company company) {
        return company.getStorageReservedBytes() == null ? 0 : company.getStorageReservedBytes();
    }

    private long quota(Company company) {
        return company.getStorageQuotaBytes() == null ? 0 : company.getStorageQuotaBytes();
    }
}
