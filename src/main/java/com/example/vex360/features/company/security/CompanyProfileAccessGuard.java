package com.example.vex360.features.company.security;

import org.springframework.stereotype.Component;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompanyProfileAccessGuard {
    private final CompanyRepository companyRepository;

    public Company requireActiveCompany(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Company company = companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
        if (company.getStatus() == CompanyStatus.INCOMPLETE_PROFILE) {
            throw new AppException(ErrorCode.COMPANY_PROFILE_INCOMPLETE);
        }
        if (company.getStatus() == CompanyStatus.ARCHIVED) {
            throw new AppException(ErrorCode.COMPANY_ARCHIVED);
        }
        return company;
    }
}
