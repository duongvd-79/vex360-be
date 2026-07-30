package com.example.vex360.features.wallet.services;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CompanyPayoutProfileService {

    CompanyPayoutProfileRepository payoutProfileRepository;
    CompanyService companyService;
    PayoutProfileEncryptionService encryptionService;

    @Transactional(readOnly = true)
    public CompanyPayoutProfileResponseDTO getProfileForOrganizer(User user) {
        Company company = getCompanyForUser(user);
        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYOUT_PROFILE_NOT_FOUND));
        return mapToResponse(profile);
    }

    @Transactional
    public CompanyPayoutProfileResponseDTO updateProfileForOrganizer(User user, UpdatePayoutProfileRequestDTO dto) {
        Company company = getCompanyForUser(user);

        EncryptedAccountData encrypted = encryptionService.encryptAccountNumber(dto.getAccountNumber(),
                company.getId());

        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(company.getId())
                .orElseGet(() -> CompanyPayoutProfile.builder().company(company).build());

        profile.setBankCode(dto.getBankCode().trim());
        profile.setBankNameSnapshot(dto.getBankNameSnapshot().trim());
        profile.setAccountNumberCiphertext(encrypted.ciphertextBase64());
        profile.setAccountNumberNonce(encrypted.nonceBase64());
        profile.setEncryptionKeyVersion(encrypted.keyVersion());
        profile.setAccountNumberLast4(encrypted.last4());
        profile.setAccountHolderName(dto.getAccountHolderName().trim().toUpperCase());
        profile.setStatus(PayoutProfileStatus.PENDING_VERIFICATION);
        profile.setVerifiedBy(null);
        profile.setVerifiedAt(null);
        profile.setRejectedBy(null);
        profile.setRejectedAt(null);
        profile.setRejectedReason(null);

        profile = payoutProfileRepository.save(profile);
        log.info("Payout profile updated for company {}. Reset status to PENDING_VERIFICATION.", company.getId());

        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyPayoutProfileResponseDTO> getProfilesForAdmin(PayoutProfileStatus status,
            Pageable pageable) {
        Page<CompanyPayoutProfile> page;
        if (status != null) {
            page = payoutProfileRepository.findByStatus(status, pageable);
        } else {
            page = payoutProfileRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional
    public CompanyPayoutProfileResponseDTO verifyProfileForAdmin(UUID companyId, User adminUser) {
        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYOUT_PROFILE_NOT_FOUND));

        profile.setStatus(PayoutProfileStatus.VERIFIED);
        profile.setVerifiedBy(adminUser);
        profile.setVerifiedAt(Instant.now());
        profile.setRejectedBy(null);
        profile.setRejectedAt(null);
        profile.setRejectedReason(null);

        profile = payoutProfileRepository.save(profile);
        log.info("Payout profile for company {} VERIFIED by admin {}", companyId, adminUser.getId());
        return mapToResponse(profile);
    }

    @Transactional
    public CompanyPayoutProfileResponseDTO rejectProfileForAdmin(UUID companyId, String reason, User adminUser) {
        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYOUT_PROFILE_NOT_FOUND));

        profile.setStatus(PayoutProfileStatus.REJECTED);
        profile.setRejectedBy(adminUser);
        profile.setRejectedAt(Instant.now());
        profile.setRejectedReason(reason != null ? reason.trim() : "Rejected by admin");
        profile.setVerifiedBy(null);
        profile.setVerifiedAt(null);

        profile = payoutProfileRepository.save(profile);
        log.info("Payout profile for company {} REJECTED by admin {}", companyId, adminUser.getId());
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public String decryptAccountNumberForAdmin(UUID companyId, User adminUser) {
        log.info("Admin {} requested decryption of bank account for company {}", adminUser.getId(), companyId);
        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYOUT_PROFILE_NOT_FOUND));

        return encryptionService.decryptAccountNumber(
                profile.getAccountNumberCiphertext(),
                profile.getAccountNumberNonce(),
                companyId);
    }

    private Company getCompanyForUser(User user) {
        return companyService.getCompanyEntityForCurrentUser(user);
    }

    private CompanyPayoutProfileResponseDTO mapToResponse(CompanyPayoutProfile profile) {
        String masked = "****" + profile.getAccountNumberLast4();
        return CompanyPayoutProfileResponseDTO.builder()
                .companyId(profile.getCompany().getId())
                .companyName(profile.getCompany().getName())
                .bankCode(profile.getBankCode())
                .bankNameSnapshot(profile.getBankNameSnapshot())
                .accountNumberMasked(masked)
                .accountHolderName(profile.getAccountHolderName())
                .status(profile.getStatus())
                .verifiedAt(profile.getVerifiedAt())
                .rejectedAt(profile.getRejectedAt())
                .rejectedReason(profile.getRejectedReason())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
