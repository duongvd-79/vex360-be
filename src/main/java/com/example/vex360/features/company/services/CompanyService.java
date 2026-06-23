package com.example.vex360.features.company.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.features.company.mapper.CompanyMapper;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    @Transactional(readOnly = true)
    public CompanyResponseDTO getCurrentUserCompany(User currentUser) {
        return companyMapper.toResponse(getCompanyForCurrentUser(currentUser));
    }

    @Transactional
    public CompanyResponseDTO updateCurrentUserCompany(User currentUser, UpdateCompanyProfileRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);

        companyMapper.updateProfile(company, request);

        if (company.getStatus() != CompanyStatus.ARCHIVED && hasCompleteProfile(company)) {
            company.setStatus(CompanyStatus.ACTIVE);
        }

        return companyMapper.toResponse(companyRepository.save(company));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private boolean hasCompleteProfile(Company company) {
        return hasText(company.getIndustry())
                && hasText(company.getDescription())
                && hasText(company.getLogoUrl())
                && hasText(company.getWebsite())
                && hasText(company.getPhone())
                && hasText(company.getAddress());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
