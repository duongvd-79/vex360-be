package com.example.vex360.features.company.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
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

    @Transactional(readOnly = true)
    public CompanyResponseDTO getCurrentUserCompany(User currentUser) {
        return toResponse(getCompanyForCurrentUser(currentUser));
    }

    @Transactional
    public CompanyResponseDTO updateCurrentUserCompany(User currentUser, UpdateCompanyProfileRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);

        if (request.getIndustry() != null) {
            company.setIndustry(request.getIndustry());
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }
        if (request.getLogoUrl() != null) {
            company.setLogoUrl(request.getLogoUrl());
        }
        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }
        if (request.getPhone() != null) {
            company.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            company.setAddress(request.getAddress());
        }

        if (company.getStatus() != CompanyStatus.ARCHIVED && hasCompleteProfile(company)) {
            company.setStatus(CompanyStatus.ACTIVE);
        }

        return toResponse(companyRepository.save(company));
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

    private CompanyResponseDTO toResponse(Company company) {
        User ownerUser = company.getOwnerUser();
        return new CompanyResponseDTO(
                company.getId(),
                ownerUser == null ? null : ownerUser.getId(),
                company.getName(),
                company.getIndustry(),
                company.getDescription(),
                company.getLogoUrl(),
                company.getWebsite(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getStatus().name());
    }
}
