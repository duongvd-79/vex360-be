package com.example.vex360.features.company.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.features.company.mapper.CompanyMapper;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.enums.Role;
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
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserId(currentUser.getId())
                .map(companyMapper::toResponse)
                .orElse(null);
    }

    @Transactional
    public CompanyResponseDTO updateCurrentUserCompany(User currentUser, UpdateCompanyProfileRequest request) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Company company = companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseGet(() -> createDefaultCompanyForUser(currentUser, request));

        companyMapper.updateProfile(company, request);

        if (company.getStatus() != CompanyStatus.ARCHIVED && hasCompleteProfile(company)) {
            company.setStatus(CompanyStatus.ACTIVE);
        }

        return companyMapper.toResponse(companyRepository.save(company));
    }

    private Company createDefaultCompanyForUser(User currentUser, UpdateCompanyProfileRequest request) {
        String name = firstNonBlank(request.getName(), currentUser.getFullName(), currentUser.getEmail());
        if (!hasText(name)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        String email = currentUser.getEmail();
        String phone = firstNonBlank(request.getPhone(), currentUser.getPhoneNumber());

        companyRepository.insertCompanyIfAbsent(
                UUID.randomUUID().toString(),
                currentUser.getId().toString(),
                name,
                email,
                phone,
                CompanyStatus.INCOMPLETE_PROFILE.name());

        return companyRepository.findByOwnerUserIdForUpdate(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNCATCHED_EXCEPTION));
    }

    @Transactional(readOnly = true)
    public Company getCompanyEntityForCurrentUser(User currentUser) {
        return getCompanyForCurrentUser(currentUser);
    }

    @Transactional
    public Company getCompanyEntityForCurrentUserForUpdate(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserIdForUpdate(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Company> findByOwnerUserId(UUID ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        return companyRepository.findByOwnerUserId(ownerUserId);
    }

    @Transactional(readOnly = true)
    public boolean existsByOwnerUserId(UUID ownerUserId) {
        return companyRepository.existsByOwnerUserId(ownerUserId);
    }

    /**
     * Ensures an eligible company-role user owns exactly one company.
     *
     * Non-company roles are an intentional no-op: the method does not query,
     * create, log, or raise a company-related error for them.
     */
    @Transactional
    public Optional<Company> ensureCompanyForCompanyRole(
            User ownerUser,
            String preferredName,
            String preferredEmail,
            String preferredPhone) {
        if (ownerUser == null || ownerUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (ownerUser.getRole() != Role.EXHIBITOR && ownerUser.getRole() != Role.ORGANIZER) {
            return Optional.empty();
        }

        Optional<Company> existingCompany = companyRepository.findByOwnerUserId(ownerUser.getId());
        if (existingCompany.isPresent()) {
            return existingCompany;
        }

        String name = firstNonBlank(preferredName, ownerUser.getFullName(), ownerUser.getEmail());
        if (name == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        String email = firstNonBlank(preferredEmail, ownerUser.getEmail());
        String phone = firstNonBlank(preferredPhone, ownerUser.getPhoneNumber());

        companyRepository.insertCompanyIfAbsent(
                UUID.randomUUID().toString(),
                ownerUser.getId().toString(),
                name,
                email,
                phone,
                CompanyStatus.INCOMPLETE_PROFILE.name());

        // This is a locking/current read, so it sees the row committed by a
        // concurrent winner even under MySQL's default REPEATABLE READ level.
        return Optional.of(companyRepository.findByOwnerUserIdForUpdate(ownerUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNCATCHED_EXCEPTION)));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public boolean isLogoAssetReferenced(String publicId) {
        return companyRepository.existsByLogoUrlContaining(publicId);
    }

    private boolean hasCompleteProfile(Company company) {
        return hasText(company.getName())
                && hasText(company.getIndustry())
                && hasText(company.getDescription())
                && hasText(company.getLogoUrl())
                && hasText(company.getWebsite())
                && hasText(company.getPhone())
                && hasText(company.getAddress());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
