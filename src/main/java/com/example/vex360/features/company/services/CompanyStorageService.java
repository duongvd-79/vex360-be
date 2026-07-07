package com.example.vex360.features.company.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        long used = company.getStorageUsedBytes();
        long quota = company.getStorageQuotaBytes();
        if (used + fileSizeBytes > quota) {
            throw new AppException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
    }

    @Transactional
    public void addUsage(Company company, long fileSizeBytes) {
        company.setStorageUsedBytes(company.getStorageUsedBytes() + fileSizeBytes);
        companyRepository.save(company);
        log.info("Storage usage updated for company {}: +{}B, total={}B",
                company.getId(), fileSizeBytes, company.getStorageUsedBytes());
    }

    @Transactional
    public void deductUsage(Company company, long fileSizeBytes) {
        long newUsed = Math.max(0, company.getStorageUsedBytes() - fileSizeBytes);
        company.setStorageUsedBytes(newUsed);
        companyRepository.save(company);
        log.info("Storage usage updated for company {}: -{}B, total={}B",
                company.getId(), fileSizeBytes, newUsed);
    }
}
