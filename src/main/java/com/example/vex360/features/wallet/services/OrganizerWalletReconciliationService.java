package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.wallet.dtos.ReconciliationReportDTO;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
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
public class OrganizerWalletReconciliationService {

    OrganizerWalletRepository walletRepository;
    OrganizerWalletTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public ReconciliationReportDTO reconcileWalletForCompany(UUID companyId) {
        OrganizerWallet wallet = walletRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        return reconcileWallet(wallet);
    }

    private ReconciliationReportDTO reconcileWallet(OrganizerWallet wallet) {
        Company company = wallet.getCompany();
        UUID companyId = company.getId();

        List<OrganizerWalletTransaction> txs = transactionRepository
                .findByWalletId(wallet.getId());

        BigDecimal pendingLedger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal availableLedger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal reservedLedger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawnLedger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (OrganizerWalletTransaction tx : txs) {
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            switch (tx.getType()) {
                case PAYMENT_CREDIT -> pendingLedger = pendingLedger.add(amt);
                case EXHIBITION_RELEASE -> {
                    pendingLedger = pendingLedger.subtract(amt);
                    availableLedger = availableLedger.add(amt);
                }
                case PAYMENT_REVERSAL -> pendingLedger = pendingLedger.subtract(amt);
                case WITHDRAWAL_RESERVED -> {
                    availableLedger = availableLedger.subtract(amt);
                    reservedLedger = reservedLedger.add(amt);
                }
                case WITHDRAWAL_RELEASED -> {
                    reservedLedger = reservedLedger.subtract(amt);
                    availableLedger = availableLedger.add(amt);
                }
                case WITHDRAWAL_PAID -> {
                    reservedLedger = reservedLedger.subtract(amt);
                    withdrawnLedger = withdrawnLedger.add(amt);
                }
            }
        }

        BigDecimal pendingDelta = wallet.getPendingBalance().subtract(pendingLedger);
        BigDecimal availableDelta = wallet.getAvailableBalance().subtract(availableLedger);
        BigDecimal reservedDelta = wallet.getReservedBalance().subtract(reservedLedger);
        BigDecimal withdrawnDelta = wallet.getWithdrawnTotal().subtract(withdrawnLedger);

        boolean isMatch = pendingDelta.compareTo(BigDecimal.ZERO) == 0
                && availableDelta.compareTo(BigDecimal.ZERO) == 0
                && reservedDelta.compareTo(BigDecimal.ZERO) == 0
                && withdrawnDelta.compareTo(BigDecimal.ZERO) == 0;

        if (!isMatch) {
            log.warn(
                    "[Wallet Integrity Alert] Mismatch detected for company ID {}! Pending delta: {}, Available delta: {}, Reserved delta: {}, Withdrawn delta: {}",
                    companyId, pendingDelta, availableDelta, reservedDelta, withdrawnDelta);
        } else {
            log.info("[Wallet Integrity OK] Wallet snapshot matches ledger for company ID {}", companyId);
        }

        return ReconciliationReportDTO.builder()
                .companyId(companyId)
                .companyName(company.getName())
                .match(isMatch)
                .pendingSnapshot(wallet.getPendingBalance())
                .pendingLedger(pendingLedger)
                .pendingDelta(pendingDelta)
                .availableSnapshot(wallet.getAvailableBalance())
                .availableLedger(availableLedger)
                .availableDelta(availableDelta)
                .reservedSnapshot(wallet.getReservedBalance())
                .reservedLedger(reservedLedger)
                .reservedDelta(reservedDelta)
                .withdrawnSnapshot(wallet.getWithdrawnTotal())
                .withdrawnLedger(withdrawnLedger)
                .withdrawnDelta(withdrawnDelta)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReconciliationReportDTO> reconcileAllWallets() {
        int page = 0;
        int batchSize = 50;
        Page<OrganizerWallet> walletPage;
        List<ReconciliationReportDTO> reports = new ArrayList<>();

        do {
            walletPage = walletRepository.findAll(
                    PageRequest.of(page, batchSize, Sort.by("id").ascending()));
            for (OrganizerWallet wallet : walletPage.getContent()) {
                reports.add(reconcileWallet(wallet));
            }
            page++;
        } while (walletPage.hasNext());

        return reports;
    }
}
