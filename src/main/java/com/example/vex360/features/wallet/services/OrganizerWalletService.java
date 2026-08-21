package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;

import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.ExhibitionWalletSummaryDTO;
import com.example.vex360.features.wallet.dtos.OrganizerWalletResponseDTO;
import com.example.vex360.features.wallet.dtos.WalletTransactionResponseDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;

import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.enums.WithdrawalStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.repositories.WithdrawalRequestRepository;

import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrganizerWalletService {

    CompanyService companyService;
    OrganizerWalletRepository walletRepository;
    OrganizerWalletTransactionRepository transactionRepository;
    CompanyPayoutProfileRepository payoutProfileRepository;
    WithdrawalRequestRepository withdrawalRequestRepository;
    ExhibitionService exhibitionService;

    public static final BigDecimal DEFAULT_MINIMUM_WITHDRAWAL = new BigDecimal("100000.00");

    @Transactional(readOnly = true)
    public OrganizerWalletResponseDTO getWalletForOrganizer(User user) {
        Company company = getCompanyForUser(user);
        OrganizerWallet wallet = walletRepository.findByCompanyId(company.getId())
                .orElseGet(() -> OrganizerWallet.builder()
                        .company(company)
                        .currency("VND")
                        .pendingBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .availableBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .reservedBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .withdrawnTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .build());

        boolean hasActiveWithdrawal = withdrawalRequestRepository.existsByCompanyIdAndStatusIn(
                company.getId(),
                List.of(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED));

        PayoutProfileStatus profileStatus = payoutProfileRepository.findByCompanyId(company.getId())
                .map(CompanyPayoutProfile::getStatus)
                .orElse(null);

        return OrganizerWalletResponseDTO.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .currency(wallet.getCurrency())
                .pendingBalance(wallet.getPendingBalance())
                .availableBalance(wallet.getAvailableBalance())
                .reservedBalance(wallet.getReservedBalance())
                .withdrawnTotal(wallet.getWithdrawnTotal())
                .minimumWithdrawalAmount(DEFAULT_MINIMUM_WITHDRAWAL)
                .hasActiveWithdrawal(hasActiveWithdrawal)
                .payoutProfileStatus(profileStatus)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponseDTO> getTransactionsForOrganizer(
            User user, UUID exhibitionUuid, WalletTransactionType type, Pageable pageable) {
        Company company = getCompanyForUser(user);

        Page<OrganizerWalletTransaction> page;
        if (exhibitionUuid != null) {
            page = transactionRepository.findByCompanyIdAndExhibitionUuid(company.getId(), exhibitionUuid, pageable);
        } else {
            page = transactionRepository.findByCompanyIdAndTypeFilter(company.getId(), type, pageable);
        }

        Page<WalletTransactionResponseDTO> dtoPage = page.map(this::mapToTransactionResponse);
        return PageResponse.from(dtoPage);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExhibitionWalletSummaryDTO> getExhibitionSummariesForOrganizer(User user, Pageable pageable) {
        PageResponse<ExhibitionResponseDTO> exhibitionsPage = exhibitionService.searchExhibitionsForOrganizer(
                user, null, null, null, null, null, pageable);

        List<Integer> exhibitionIds = exhibitionsPage.getContent().stream()
                .map(ExhibitionResponseDTO::getId)
                .toList();

        Map<Integer, List<OrganizerWalletTransaction>> txsByExhibitionMap = new HashMap<>();
        if (!exhibitionIds.isEmpty()) {
            List<OrganizerWalletTransaction> allTxs = transactionRepository.findByExhibitionIdIn(exhibitionIds);
            for (OrganizerWalletTransaction tx : allTxs) {
                if (tx.getExhibition() != null && tx.getExhibition().getId() != null) {
                    txsByExhibitionMap.computeIfAbsent(tx.getExhibition().getId(), k -> new ArrayList<>()).add(tx);
                }
            }
        }

        List<ExhibitionWalletSummaryDTO> list = new ArrayList<>();
        for (ExhibitionResponseDTO ex : exhibitionsPage.getContent()) {
            List<OrganizerWalletTransaction> txs = txsByExhibitionMap.getOrDefault(ex.getId(), List.of());

            long paidCount = 0;
            BigDecimal gross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal pending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal available = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal reversed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            for (OrganizerWalletTransaction tx : txs) {
                if (tx.getType() == WalletTransactionType.PAYMENT_CREDIT) {
                    paidCount++;
                    BigDecimal txAmount = tx.getAmount();
                    net = net.add(txAmount);
                    if (tx.getPayment() != null) {
                        gross = gross.add(tx.getPayment().getAmount() != null ? tx.getPayment().getAmount() : txAmount);
                        fee = fee.add(tx.getPayment().getSystemFee() != null ? tx.getPayment().getSystemFee()
                                : BigDecimal.ZERO);
                    } else {
                        gross = gross.add(txAmount);
                    }
                    pending = pending.add(txAmount);
                } else if (tx.getType() == WalletTransactionType.EXHIBITION_RELEASE) {
                    pending = pending.subtract(tx.getAmount());
                    available = available.add(tx.getAmount());
                } else if (tx.getType() == WalletTransactionType.PAYMENT_REVERSAL) {
                    reversed = reversed.add(tx.getAmount());
                    pending = pending.subtract(tx.getAmount());
                }
            }

            list.add(ExhibitionWalletSummaryDTO.builder()
                    .exhibitionUuid(ex.getUuid())
                    .exhibitionName(ex.getName())
                    .status(ExhibitionStatus.valueOf(ex.getStatus()))
                    .startDate(ex.getStartDate())
                    .endDate(ex.getEndDate())
                    .paidRegistrationCount(paidCount)
                    .grossRevenue(gross)
                    .systemFeeTotal(fee)
                    .organizerNetRevenue(net)
                    .pendingBalance(pending.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pending)
                    .availableBalance(available)
                    .reversedAmount(reversed)
                    .build());
        }

        return PageResponse.from(new PageImpl<>(list, pageable, exhibitionsPage.getTotalElements()));
    }

    private Company getCompanyForUser(User user) {
        return companyService.getCompanyEntityForCurrentUser(user);
    }

    private WalletTransactionResponseDTO mapToTransactionResponse(OrganizerWalletTransaction tx) {
        UUID exhUuid = tx.getExhibition() != null ? tx.getExhibition().getUuid() : null;
        String exhName = tx.getExhibition() != null ? tx.getExhibition().getName() : null;
        String exhibitorName = tx.getPayment() != null && tx.getPayment().getExhibitorRegistration() != null
                && tx.getPayment().getExhibitorRegistration().getCompany() != null
                        ? tx.getPayment().getExhibitorRegistration().getCompany().getName()
                        : null;
        UUID regUuid = tx.getPayment() != null && tx.getPayment().getExhibitorRegistration() != null
                ? tx.getPayment().getExhibitorRegistration().getUuid()
                : null;
        Long orderCode = tx.getPayment() != null ? tx.getPayment().getOrderCode() : null;

        BigDecimal amount = tx.getAmount();
        BigDecimal sysFee = tx.getPayment() != null ? tx.getPayment().getSystemFee() : BigDecimal.ZERO;
        BigDecimal payout = tx.getPayment() != null ? tx.getPayment().getOrganizerPayout() : amount;

        return WalletTransactionResponseDTO.builder()
                .transactionUuid(tx.getUuid())
                .exhibitionUuid(exhUuid)
                .exhibitionName(exhName)
                .exhibitorCompanyName(exhibitorName)
                .registrationUuid(regUuid)
                .orderCode(orderCode)
                .amount(amount)
                .systemFee(sysFee)
                .organizerPayout(payout)
                .type(tx.getType())
                .fromBucket(tx.getFromBucket())
                .toBucket(tx.getToBucket())
                .pendingAfter(tx.getPendingAfter())
                .availableAfter(tx.getAvailableAfter())
                .reservedAfter(tx.getReservedAfter())
                .withdrawnAfter(tx.getWithdrawnAfter())
                .reason(tx.getReason())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
