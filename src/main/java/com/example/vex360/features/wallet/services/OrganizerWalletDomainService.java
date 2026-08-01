package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.enums.WalletBucket;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.shared.enums.ExhibitionStatus;
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
public class OrganizerWalletDomainService {

    OrganizerWalletRepository organizerWalletRepository;
    OrganizerWalletTransactionRepository organizerWalletTransactionRepository;

    @Transactional
    public OrganizerWallet getOrCreateWallet(Company company) {
        return organizerWalletRepository.findByCompanyId(company.getId())
                .orElseGet(() -> {
                    OrganizerWallet newWallet = OrganizerWallet.builder()
                            .company(company)
                            .currency("VND")
                            .pendingBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .availableBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .reservedBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .withdrawnTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .build();
                    return organizerWalletRepository.save(newWallet);
                });
    }

    @Transactional
    public OrganizerWallet getWalletWithLock(Company company) {
        return organizerWalletRepository.findWithLockByCompanyId(company.getId())
                .orElseGet(() -> {
                    getOrCreateWallet(company);
                    return organizerWalletRepository.findWithLockByCompanyId(company.getId())
                            .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
                });
    }

    @Transactional
    public OrganizerWalletTransaction creditPendingPayment(Company company, Exhibition exhibition, Payment payment,
            BigDecimal amount, String reason, User actor) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Attempted to credit payment with zero or negative amount: {}", amount);
            return null;
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        OrganizerWallet wallet = getWalletWithLock(company);

        if (payment != null && payment.getId() != null &&
                organizerWalletTransactionRepository.existsByPaymentIdAndType(payment.getId(),
                        WalletTransactionType.PAYMENT_CREDIT)) {
            log.info("Payment ID {} already credited (PAYMENT_CREDIT exist). Skipping for idempotency.",
                    payment.getId());
            return null;
        }

        BigDecimal newPending = wallet.getPendingBalance().add(amount);
        wallet.setPendingBalance(newPending);

        OrganizerWalletTransaction tx = ledgerEntry(wallet, company)
                .exhibition(exhibition)
                .payment(payment)
                .type(WalletTransactionType.PAYMENT_CREDIT)
                .amount(amount)
                .fromBucket(WalletBucket.VOID)
                .toBucket(WalletBucket.PENDING)
                .actorUser(actor)
                .reason(reason != null ? reason : "Payment revenue credited to pending")
                .build();

        try {
            return organizerWalletTransactionRepository.save(tx);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Duplicate PAYMENT_CREDIT transaction detected for payment ID {}. Idempotent fallback triggered.",
                    payment.getId());
            return null;
        }
    }

    @Transactional
    public OrganizerWalletTransaction releasePendingRevenue(Company company, Exhibition exhibition,
            BigDecimal amountToRelease, String reason, User actor) {
        if (amountToRelease == null || amountToRelease.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        amountToRelease = amountToRelease.setScale(2, RoundingMode.HALF_UP);

        OrganizerWallet wallet = getWalletWithLock(company);
        if (wallet.getPendingBalance().compareTo(amountToRelease) < 0) {
            log.warn(
                    "Pending balance ({}) is less than release amount ({}) for company {}. Releasing available pending amount.",
                    wallet.getPendingBalance(), amountToRelease, company.getId());
            amountToRelease = wallet.getPendingBalance();
        }

        if (amountToRelease.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amountToRelease));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amountToRelease));

        OrganizerWalletTransaction tx = ledgerEntry(wallet, company)
                .exhibition(exhibition)
                .type(WalletTransactionType.EXHIBITION_RELEASE)
                .amount(amountToRelease)
                .fromBucket(WalletBucket.PENDING)
                .toBucket(WalletBucket.AVAILABLE)
                .actorUser(actor)
                .reason(reason != null ? reason : "Exhibition completed - pending balance released to available")
                .build();

        return organizerWalletTransactionRepository.save(tx);
    }

    @Transactional
    public OrganizerWalletTransaction releaseCompletedExhibitionRevenue(Company company, Exhibition exhibition) {
        organizerWalletRepository.findWithLockByCompanyId(company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        BigDecimal credited = organizerWalletTransactionRepository
                .sumCreditedAmountByExhibitionId(exhibition.getId());
        BigDecimal released = organizerWalletTransactionRepository
                .sumReleasedAmountByExhibitionId(exhibition.getId());
        BigDecimal reversed = organizerWalletTransactionRepository
                .sumReversedAmountByExhibitionId(exhibition.getId());
        return releasePendingRevenue(
                company,
                exhibition,
                credited.subtract(released).subtract(reversed),
                "Lifecycle completion release for exhibition ID " + exhibition.getId(),
                null);
    }

    @Transactional
    public OrganizerWalletTransaction reserveWithdrawal(Company company, WithdrawalRequest withdrawal,
            BigDecimal amount, User actor) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        OrganizerWallet wallet = getWalletWithLock(company);

        if (withdrawal != null && withdrawal.getId() != null &&
                organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(withdrawal.getId(),
                        WalletTransactionType.WITHDRAWAL_RESERVED)) {
            log.info("Withdrawal ID {} already reserved. Skipping.", withdrawal.getId());
            return null;
        }

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setReservedBalance(wallet.getReservedBalance().add(amount));

        OrganizerWalletTransaction tx = ledgerEntry(wallet, company)
                .withdrawalRequest(withdrawal)
                .type(WalletTransactionType.WITHDRAWAL_RESERVED)
                .amount(amount)
                .fromBucket(WalletBucket.AVAILABLE)
                .toBucket(WalletBucket.RESERVED)
                .actorUser(actor)
                .reason("Withdrawal request created - funds reserved")
                .build();

        try {
            return organizerWalletTransactionRepository.save(tx);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn(
                    "Duplicate WITHDRAWAL_RESERVED transaction detected for withdrawal ID {}. Idempotent fallback triggered.",
                    withdrawal.getId());
            return null;
        }
    }

    @Transactional
    public OrganizerWalletTransaction releaseWithdrawalReserve(Company company, WithdrawalRequest withdrawal,
            String reason, User actor) {
        OrganizerWallet wallet = getWalletWithLock(company);

        if (withdrawal != null && withdrawal.getId() != null &&
                organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(withdrawal.getId(),
                        WalletTransactionType.WITHDRAWAL_RELEASED)) {
            log.info("Withdrawal ID {} reserve already released. Skipping.", withdrawal.getId());
            return null;
        }

        BigDecimal amount = withdrawal.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (wallet.getReservedBalance().compareTo(amount) < 0) {
            amount = wallet.getReservedBalance();
        }

        wallet.setReservedBalance(wallet.getReservedBalance().subtract(amount));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));

        OrganizerWalletTransaction tx = ledgerEntry(wallet, company)
                .withdrawalRequest(withdrawal)
                .type(WalletTransactionType.WITHDRAWAL_RELEASED)
                .amount(amount)
                .fromBucket(WalletBucket.RESERVED)
                .toBucket(WalletBucket.AVAILABLE)
                .actorUser(actor)
                .reason(reason != null ? reason : "Withdrawal canceled or rejected - funds released to available")
                .build();

        try {
            return organizerWalletTransactionRepository.save(tx);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn(
                    "Duplicate WITHDRAWAL_RELEASED transaction detected for withdrawal ID {}. Idempotent fallback triggered.",
                    withdrawal.getId());
            return null;
        }
    }

    @Transactional
    public OrganizerWalletTransaction markWithdrawalPaid(Company company, WithdrawalRequest withdrawal, User actor) {
        OrganizerWallet wallet = getWalletWithLock(company);

        if (withdrawal != null && withdrawal.getId() != null &&
                organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(withdrawal.getId(),
                        WalletTransactionType.WITHDRAWAL_PAID)) {
            log.info("Withdrawal ID {} already marked paid. Skipping.", withdrawal.getId());
            return null;
        }

        BigDecimal amount = withdrawal.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (wallet.getReservedBalance().compareTo(amount) < 0) {
            amount = wallet.getReservedBalance();
        }

        wallet.setReservedBalance(wallet.getReservedBalance().subtract(amount));
        wallet.setWithdrawnTotal(wallet.getWithdrawnTotal().add(amount));

        OrganizerWalletTransaction tx = ledgerEntry(wallet, company)
                .withdrawalRequest(withdrawal)
                .type(WalletTransactionType.WITHDRAWAL_PAID)
                .amount(amount)
                .fromBucket(WalletBucket.RESERVED)
                .toBucket(WalletBucket.WITHDRAWN)
                .actorUser(actor)
                .reason("Withdrawal paid via bank transfer")
                .build();

        try {
            return organizerWalletTransactionRepository.save(tx);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn(
                    "Duplicate WITHDRAWAL_PAID transaction detected for withdrawal ID {}. Idempotent fallback triggered.",
                    withdrawal.getId());
            return null;
        }
    }

    @Transactional
    public OrganizerWalletTransaction reversePendingPayment(Integer paymentId, String reason, User actor) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment ID cannot be null for reversal");
        }

        OrganizerWalletTransaction creditTx = organizerWalletTransactionRepository
                .findByPaymentIdAndType(paymentId, WalletTransactionType.PAYMENT_CREDIT)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_CREDITED));
        Payment payment = creditTx.getPayment();

        Company targetCompany = creditTx.getCompany();
        OrganizerWallet wallet = getWalletWithLock(targetCompany);

        if (organizerWalletTransactionRepository.existsByPaymentIdAndType(paymentId,
                WalletTransactionType.PAYMENT_REVERSAL)) {
            log.info("Payment ID {} already reversed. Skipping.", paymentId);
            return null;
        }

        Exhibition exhibition = creditTx.getExhibition();
        if (exhibition != null && exhibition.getStatus() == ExhibitionStatus.COMPLETED) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_RELEASED);
        }

        BigDecimal payout = creditTx.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (wallet.getPendingBalance().compareTo(payout) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }

        wallet.setPendingBalance(wallet.getPendingBalance().subtract(payout));

        OrganizerWalletTransaction tx = ledgerEntry(wallet, targetCompany)
                .exhibition(exhibition)
                .payment(payment)
                .type(WalletTransactionType.PAYMENT_REVERSAL)
                .amount(payout)
                .fromBucket(WalletBucket.PENDING)
                .toBucket(WalletBucket.VOID)
                .actorUser(actor)
                .reason(reason != null ? reason : "Payment reversal executed by admin")
                .build();

        try {
            return organizerWalletTransactionRepository.save(tx);
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "Duplicate PAYMENT_REVERSAL transaction detected for payment ID {}. Idempotent fallback triggered.",
                    paymentId);
            return null;
        }
    }

    private OrganizerWalletTransaction.OrganizerWalletTransactionBuilder ledgerEntry(
            OrganizerWallet wallet, Company company) {
        return OrganizerWalletTransaction.builder()
                .uuid(UUID.randomUUID())
                .wallet(wallet)
                .company(company)
                .pendingAfter(wallet.getPendingBalance())
                .availableAfter(wallet.getAvailableBalance())
                .reservedAfter(wallet.getReservedBalance())
                .withdrawnAfter(wallet.getWithdrawnTotal());
    }
}
