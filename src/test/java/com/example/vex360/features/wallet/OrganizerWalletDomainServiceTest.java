package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.enums.WalletBucket;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrganizerWalletDomainServiceTest {

    @Mock
    private OrganizerWalletRepository organizerWalletRepository;

    @Mock
    private OrganizerWalletTransactionRepository organizerWalletTransactionRepository;

    @InjectMocks
    private OrganizerWalletDomainService domainService;

    private Company company;
    private OrganizerWallet wallet;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Test Company")
                .build();

        wallet = OrganizerWallet.builder()
                .id(UUID.randomUUID())
                .company(company)
                .pendingBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .withdrawnTotal(BigDecimal.ZERO)
                .build();
    }

    @Test
    void creditPendingPayment_Success() {
        Payment payment = Payment.builder().id(100).build();
        Exhibition exhibition = Exhibition.builder().id(1).build();
        BigDecimal payout = new BigDecimal("500000.00");

        when(organizerWalletTransactionRepository.existsByPaymentIdAndType(100, WalletTransactionType.PAYMENT_CREDIT))
                .thenReturn(false);
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizerWalletTransaction tx = domainService.creditPendingPayment(company, exhibition, payment, payout,
                "Test credit", null);

        assertNotNull(tx);
        assertEquals(WalletTransactionType.PAYMENT_CREDIT, tx.getType());
        assertEquals(new BigDecimal("500000.00"), wallet.getPendingBalance());
        assertEquals(WalletBucket.VOID, tx.getFromBucket());
        assertEquals(WalletBucket.PENDING, tx.getToBucket());
    }

    @Test
    void creditPendingPayment_Idempotent_ReturnsNull() {
        Payment payment = Payment.builder().id(100).build();
        BigDecimal payout = new BigDecimal("500000.00");

        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByPaymentIdAndType(100, WalletTransactionType.PAYMENT_CREDIT))
                .thenReturn(true);

        OrganizerWalletTransaction tx = domainService.creditPendingPayment(company, null, payment, payout,
                "Test credit", null);

        assertEquals(null, tx);
    }

    @Test
    void releasePendingRevenue_Success() {
        wallet.setPendingBalance(new BigDecimal("500000.00"));

        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizerWalletTransaction tx = domainService.releasePendingRevenue(company, null, new BigDecimal("500000.00"),
                "Release", null);

        assertNotNull(tx);
        assertEquals(WalletTransactionType.EXHIBITION_RELEASE, tx.getType());
        assertEquals(new BigDecimal("0.00"), wallet.getPendingBalance());
        assertEquals(new BigDecimal("500000.00"), wallet.getAvailableBalance());
    }

    @Test
    void releaseCompletedExhibitionRevenueReleasesOnlyRemainingCredit() {
        Exhibition exhibition = Exhibition.builder().id(1).build();
        wallet.setPendingBalance(new BigDecimal("100.00"));
        when(organizerWalletTransactionRepository.sumCreditedAmountByExhibitionId(1))
                .thenReturn(new BigDecimal("100.00"));
        when(organizerWalletTransactionRepository.sumReleasedAmountByExhibitionId(1))
                .thenReturn(new BigDecimal("20.00"));
        when(organizerWalletTransactionRepository.sumReversedAmountByExhibitionId(1))
                .thenReturn(new BigDecimal("10.00"));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        domainService.releaseCompletedExhibitionRevenue(company, exhibition);

        assertEquals(new BigDecimal("30.00"), wallet.getPendingBalance());
        assertEquals(new BigDecimal("70.00"), wallet.getAvailableBalance());
        verify(organizerWalletTransactionRepository).save(any(OrganizerWalletTransaction.class));
    }

    @Test
    void releaseCompletedExhibitionRevenueFailsWhenWalletIsMissing() {
        Exhibition exhibition = Exhibition.builder().id(1).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> domainService.releaseCompletedExhibitionRevenue(company, exhibition));

        assertSame(ErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void duplicateCompletionDoesNotReleaseRevenueTwice() {
        Exhibition exhibition = Exhibition.builder().id(1).build();
        wallet.setPendingBalance(new BigDecimal("100.00"));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.sumCreditedAmountByExhibitionId(1))
                .thenReturn(new BigDecimal("100.00"));
        when(organizerWalletTransactionRepository.sumReleasedAmountByExhibitionId(1))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("100.00"));
        when(organizerWalletTransactionRepository.sumReversedAmountByExhibitionId(1))
                .thenReturn(BigDecimal.ZERO);
        when(organizerWalletTransactionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        domainService.releaseCompletedExhibitionRevenue(company, exhibition);
        domainService.releaseCompletedExhibitionRevenue(company, exhibition);

        assertEquals(new BigDecimal("100.00"), wallet.getAvailableBalance());
        verify(organizerWalletTransactionRepository, times(1)).save(any());
    }

    @Test
    void reversePendingPayment_UsesCreditedPaymentAndDebitsPending() {
        Payment payment = Payment.builder().id(100).build();
        Exhibition exhibition = Exhibition.builder().id(1).status(ExhibitionStatus.ACTIVE).build();
        wallet.setPendingBalance(new BigDecimal("500000.00"));
        OrganizerWalletTransaction credit = OrganizerWalletTransaction.builder()
                .company(company)
                .exhibition(exhibition)
                .payment(payment)
                .amount(new BigDecimal("500000.00"))
                .build();

        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.of(credit));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_REVERSAL)).thenReturn(false);
        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizerWalletTransaction tx = domainService.reversePendingPayment(100, "Admin reversal", null);

        assertEquals(WalletTransactionType.PAYMENT_REVERSAL, tx.getType());
        assertEquals(payment, tx.getPayment());
        assertEquals(new BigDecimal("0.00"), wallet.getPendingBalance());
        assertEquals(WalletBucket.PENDING, tx.getFromBucket());
        assertEquals(WalletBucket.VOID, tx.getToBucket());
    }

    @Test
    void reserveWithdrawal_Success() {
        wallet.setAvailableBalance(new BigDecimal("1000000.00"));
        WithdrawalRequest req = WithdrawalRequest.builder().id(50L).build();

        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(50L,
                WalletTransactionType.WITHDRAWAL_RESERVED))
                .thenReturn(false);
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizerWalletTransaction tx = domainService.reserveWithdrawal(company, req, new BigDecimal("400000.00"),
                null);

        assertNotNull(tx);
        assertEquals(new BigDecimal("600000.00"), wallet.getAvailableBalance());
        assertEquals(new BigDecimal("400000.00"), wallet.getReservedBalance());
    }

    @Test
    void reserveWithdrawal_InsufficientBalance_ThrowsException() {
        wallet.setAvailableBalance(new BigDecimal("100000.00"));
        WithdrawalRequest req = WithdrawalRequest.builder().id(50L).build();

        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(50L,
                WalletTransactionType.WITHDRAWAL_RESERVED))
                .thenReturn(false);
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet));

        assertThrows(AppException.class,
                () -> domainService.reserveWithdrawal(company, req, new BigDecimal("400000.00"), null));
    }
}
