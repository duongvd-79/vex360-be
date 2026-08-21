package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.AdminMarkPaidWithdrawalRequestDTO;
import com.example.vex360.features.wallet.dtos.CreateWithdrawalRequestDTO;
import com.example.vex360.features.wallet.dtos.WithdrawalRequestResponseDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.enums.WithdrawalStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.repositories.WithdrawalRequestRepository;
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
public class WithdrawalRequestService {

    WithdrawalRequestRepository withdrawalRequestRepository;
    CompanyService companyService;
    CompanyPayoutProfileRepository payoutProfileRepository;
    OrganizerWalletDomainService walletDomainService;

    public static final BigDecimal MINIMUM_WITHDRAWAL_AMOUNT = new BigDecimal("100000.00");

    @Transactional
    public WithdrawalRequestResponseDTO createWithdrawalRequest(User user, CreateWithdrawalRequestDTO dto) {
        Company company = getCompanyForUser(user);

        CompanyPayoutProfile profile = payoutProfileRepository.findByCompanyId(company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYOUT_PROFILE_NOT_FOUND));

        if (profile.getStatus() != PayoutProfileStatus.VERIFIED) {
            throw new AppException(ErrorCode.PAYOUT_PROFILE_NOT_VERIFIED);
        }

        // C3 Invariant: Lock wallet FIRST before active request check
        OrganizerWallet wallet = walletDomainService.getWalletWithLock(company);

        boolean hasActive = withdrawalRequestRepository.existsByCompanyIdAndStatusIn(
                company.getId(),
                List.of(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED));
        if (hasActive) {
            throw new AppException(ErrorCode.ACTIVE_WITHDRAWAL_EXISTS);
        }

        BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MINIMUM_WITHDRAWAL_AMOUNT) < 0) {
            throw new AppException(ErrorCode.WITHDRAWAL_AMOUNT_BELOW_MINIMUM);
        }

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }

        WithdrawalRequest request = WithdrawalRequest.builder()
                .uuid(UUID.randomUUID())
                .wallet(wallet)
                .company(company)
                .amount(amount)
                .currency("VND")
                .minimumAmountSnapshot(MINIMUM_WITHDRAWAL_AMOUNT)
                .status(WithdrawalStatus.PENDING)
                .bankCodeSnapshot(profile.getBankCode())
                .bankNameSnapshot(profile.getBankNameSnapshot())
                .accountNumberSnapshot(profile.getAccountNumber())
                .accountHolderNameSnapshot(profile.getAccountHolderName())
                .requestedBy(user)
                .build();

        request = withdrawalRequestRepository.save(request);

        walletDomainService.reserveWithdrawal(company, request, amount, user);
        log.info("Withdrawal request {} created for company {} amount {}", request.getUuid(), company.getId(), amount);

        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<WithdrawalRequestResponseDTO> getWithdrawalRequestsForOrganizer(User user, Pageable pageable) {
        Company company = getCompanyForUser(user);
        Page<WithdrawalRequest> page = withdrawalRequestRepository.findByCompanyId(company.getId(), pageable);
        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional(readOnly = true)
    public WithdrawalRequestResponseDTO getWithdrawalRequestDetailsForOrganizer(User user, UUID uuid) {
        Company company = getCompanyForUser(user);
        WithdrawalRequest request = withdrawalRequestRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (!request.getCompany().getId().equals(company.getId())) {
            throw new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND);
        }
        return mapToResponse(request);
    }

    @Transactional
    public WithdrawalRequestResponseDTO cancelWithdrawalRequestForOrganizer(User user, UUID uuid) {
        Company company = getCompanyForUser(user);
        WithdrawalRequest request = withdrawalRequestRepository.findWithLockByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (!request.getCompany().getId().equals(company.getId())) {
            throw new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND);
        }

        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_TRANSITION);
        }

        request.setStatus(WithdrawalStatus.CANCELED);
        request.setCanceledBy(user);
        request.setCanceledAt(Instant.now());
        request = withdrawalRequestRepository.save(request);

        walletDomainService.releaseWithdrawalReserve(company, request, "Canceled by organizer", user);
        log.info("Withdrawal request {} CANCELED by organizer {}", uuid, user.getId());

        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<WithdrawalRequestResponseDTO> getWithdrawalRequestsForAdmin(
            WithdrawalStatus status, UUID companyId, Pageable pageable) {
        Page<WithdrawalRequest> page = withdrawalRequestRepository.findByAdminFilter(status, companyId, pageable);
        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional(readOnly = true)
    public WithdrawalRequestResponseDTO getWithdrawalRequestDetailsForAdmin(UUID uuid) {
        WithdrawalRequest request = withdrawalRequestRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));
        return mapToResponse(request);
    }

    @Transactional
    public WithdrawalRequestResponseDTO approveWithdrawalRequestForAdmin(User adminUser, UUID uuid) {
        WithdrawalRequest request = withdrawalRequestRepository.findWithLockByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_TRANSITION);
        }

        request.setStatus(WithdrawalStatus.APPROVED);
        request.setApprovedBy(adminUser);
        request.setApprovedAt(Instant.now());
        request = withdrawalRequestRepository.save(request);

        log.info("Withdrawal request {} APPROVED by admin {}", uuid, adminUser.getId());
        return mapToResponse(request);
    }

    @Transactional
    public WithdrawalRequestResponseDTO rejectWithdrawalRequestForAdmin(User adminUser, UUID uuid, String reason) {
        WithdrawalRequest request = withdrawalRequestRepository.findWithLockByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (request.getStatus() != WithdrawalStatus.PENDING && request.getStatus() != WithdrawalStatus.APPROVED) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_TRANSITION);
        }

        request.setStatus(WithdrawalStatus.REJECTED);
        request.setRejectedBy(adminUser);
        request.setRejectedAt(Instant.now());
        request.setRejectedReason(reason != null ? reason.trim() : "Rejected by admin");
        request = withdrawalRequestRepository.save(request);

        walletDomainService.releaseWithdrawalReserve(request.getCompany(), request, reason, adminUser);
        log.info("Withdrawal request {} REJECTED by admin {}", uuid, adminUser.getId());

        return mapToResponse(request);
    }

    @Transactional
    public WithdrawalRequestResponseDTO markPaidWithdrawalRequestForAdmin(
            User adminUser, UUID uuid, AdminMarkPaidWithdrawalRequestDTO dto) {
        WithdrawalRequest request = withdrawalRequestRepository.findWithLockByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (request.getStatus() != WithdrawalStatus.APPROVED) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_TRANSITION);
        }

        String ref = dto.getTransferReference().trim();
        if (withdrawalRequestRepository.existsByTransferReference(ref)) {
            throw new AppException(ErrorCode.TRANSFER_REFERENCE_DUPLICATED);
        }

        request.setStatus(WithdrawalStatus.PAID);
        request.setPaidBy(adminUser);
        request.setPaidAt(Instant.now());
        request.setTransferReference(ref);
        if (dto.getProofUrl() != null && !dto.getProofUrl().trim().isEmpty()) {
            request.setProofUrl(dto.getProofUrl().trim());
        }
        request = withdrawalRequestRepository.save(request);

        walletDomainService.markWithdrawalPaid(request.getCompany(), request, adminUser);
        log.info("Withdrawal request {} marked PAID by admin {} with ref {}", uuid, adminUser.getId(), ref);

        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public String getFullWithdrawalAccountNumberForAdmin(UUID uuid) {
        WithdrawalRequest request = withdrawalRequestRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        return request.getAccountNumberSnapshot();
    }

    private Company getCompanyForUser(User user) {
        return companyService.getCompanyEntityForCurrentUser(user);
    }

    @Transactional(readOnly = true)
    public boolean isProofAssetReferenced(String publicId) {
        return withdrawalRequestRepository.existsByProofUrlContaining(publicId);
    }

    private WithdrawalRequestResponseDTO mapToResponse(WithdrawalRequest request) {
        String accNum = request.getAccountNumberSnapshot();
        String masked = (accNum != null && accNum.length() >= 4)
                ? "****" + accNum.substring(accNum.length() - 4)
                : "****";
        return WithdrawalRequestResponseDTO.builder()
                .uuid(request.getUuid())
                .companyId(request.getCompany().getId())
                .companyName(request.getCompany().getName())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .minimumAmountSnapshot(request.getMinimumAmountSnapshot())
                .status(request.getStatus())
                .bankCodeSnapshot(request.getBankCodeSnapshot())
                .bankNameSnapshot(request.getBankNameSnapshot())
                .accountNumberMaskedSnapshot(masked)
                .accountNumberSnapshot(accNum)
                .accountHolderNameSnapshot(request.getAccountHolderNameSnapshot())
                .requestedAt(request.getRequestedAt())
                .approvedAt(request.getApprovedAt())
                .paidAt(request.getPaidAt())
                .rejectedAt(request.getRejectedAt())
                .rejectedReason(request.getRejectedReason())
                .canceledAt(request.getCanceledAt())
                .transferReference(request.getTransferReference())
                .proofUrl(request.getProofUrl())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
