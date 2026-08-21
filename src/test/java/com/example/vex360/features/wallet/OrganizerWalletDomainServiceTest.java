package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    void getOrCreateWalletReturnsExistingOrCreatesZeroBalanceWallet() {
        when(organizerWalletRepository.findByCompanyId(company.getId()))
                .thenReturn(Optional.of(wallet), Optional.empty());
        when(organizerWalletRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertSame(wallet, domainService.getOrCreateWallet(company));
        OrganizerWallet created = domainService.getOrCreateWallet(company);

        assertEquals(company, created.getCompany());
        assertEquals("VND", created.getCurrency());
        assertEquals(new BigDecimal("0.00"), created.getPendingBalance());
        assertEquals(new BigDecimal("0.00"), created.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), created.getReservedBalance());
        assertEquals(new BigDecimal("0.00"), created.getWithdrawnTotal());
    }

    @Test
    void getWalletWithLockCreatesThenReloadsWallet() {
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId()))
                .thenReturn(Optional.empty(), Optional.of(wallet));
        when(organizerWalletRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(wallet));

        assertSame(wallet, domainService.getWalletWithLock(company));
    }

    @Test
    void getWalletWithLockThrowsWhenReloadStillCannotFindWallet() {
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(organizerWalletRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(wallet));

        AppException exception = assertThrows(AppException.class,
                () -> domainService.getWalletWithLock(company));

        assertSame(ErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void creditPendingPaymentRejectsInvalidAmountsAndHandlesConstraintRace() {
        assertNull(domainService.creditPendingPayment(company, null, null, null, null, null));
        assertNull(domainService.creditPendingPayment(company, null, null, BigDecimal.ZERO, null, null));

        Payment payment = Payment.builder().id(100).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(false);
        when(organizerWalletTransactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertNull(domainService.creditPendingPayment(
                company, null, payment, new BigDecimal("10.126"), null, null));
        assertEquals(new BigDecimal("10.13"), wallet.getPendingBalance());
    }

    @Test
    void creditPendingPaymentAllowsPaymentWithoutIdAndUsesDefaultReason() {
        Payment payment = Payment.builder().build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizerWalletTransaction tx = domainService.creditPendingPayment(
                company, null, payment, BigDecimal.ONE, null, null);

        assertEquals("Payment revenue credited to pending", tx.getReason());
    }

    @Test
    void releasePendingRevenueHandlesInvalidAndCappedAmounts() {
        assertNull(domainService.releasePendingRevenue(company, null, null, null, null));
        assertNull(domainService.releasePendingRevenue(company, null, BigDecimal.ZERO, null, null));

        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        assertNull(domainService.releasePendingRevenue(company, null, BigDecimal.TEN, null, null));

        wallet.setPendingBalance(new BigDecimal("5.00"));
        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizerWalletTransaction tx =
                domainService.releasePendingRevenue(company, null, BigDecimal.TEN, null, null);

        assertEquals(new BigDecimal("5.00"), tx.getAmount());
        assertEquals("Exhibition completed - pending balance released to available", tx.getReason());
    }

    @Test
    void reserveWithdrawalSkipsDuplicateAndHandlesConstraintRace() {
        wallet.setAvailableBalance(new BigDecimal("20.00"));
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(7L).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(
                7L, WalletTransactionType.WITHDRAWAL_RESERVED)).thenReturn(true, false);

        assertNull(domainService.reserveWithdrawal(company, withdrawal, BigDecimal.TEN, null));

        when(organizerWalletTransactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        assertNull(domainService.reserveWithdrawal(company, withdrawal, BigDecimal.TEN, null));
    }

    @Test
    void reserveWithdrawalAllowsRequestWithoutId() {
        wallet.setAvailableBalance(BigDecimal.TEN);
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(domainService.reserveWithdrawal(company, withdrawal, BigDecimal.ONE, null));
    }

    @Test
    void releaseWithdrawalReserveSkipsDuplicateAndCapsToReservedBalance() {
        wallet.setReservedBalance(new BigDecimal("5.00"));
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(7L)
                .amount(BigDecimal.TEN)
                .build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(
                7L, WalletTransactionType.WITHDRAWAL_RELEASED)).thenReturn(true, false);

        assertNull(domainService.releaseWithdrawalReserve(company, withdrawal, null, null));

        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizerWalletTransaction tx =
                domainService.releaseWithdrawalReserve(company, withdrawal, null, null);

        assertEquals(new BigDecimal("5.00"), tx.getAmount());
        assertEquals(new BigDecimal("5.00"), wallet.getAvailableBalance());
        assertEquals("Withdrawal canceled or rejected - funds released to available", tx.getReason());
    }

    @Test
    void releaseWithdrawalReserveHandlesConstraintRaceAndExplicitReason() {
        wallet.setReservedBalance(BigDecimal.TEN);
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(7L).amount(BigDecimal.ONE).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(
                7L, WalletTransactionType.WITHDRAWAL_RELEASED)).thenReturn(false);
        when(organizerWalletTransactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertNull(domainService.releaseWithdrawalReserve(company, withdrawal, "rejected", null));
    }

    @Test
    void markWithdrawalPaidSkipsDuplicateAndCapsToReservedBalance() {
        wallet.setReservedBalance(new BigDecimal("5.00"));
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(7L).amount(BigDecimal.TEN).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByWithdrawalRequestIdAndType(
                7L, WalletTransactionType.WITHDRAWAL_PAID)).thenReturn(true, false);

        assertNull(domainService.markWithdrawalPaid(company, withdrawal, null));

        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizerWalletTransaction tx = domainService.markWithdrawalPaid(company, withdrawal, null);

        assertEquals(new BigDecimal("5.00"), tx.getAmount());
        assertEquals(new BigDecimal("5.00"), wallet.getWithdrawnTotal());
    }

    @Test
    void markWithdrawalPaidHandlesConstraintRaceAndRequestWithoutId() {
        wallet.setReservedBalance(BigDecimal.TEN);
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().amount(BigDecimal.ONE).build();
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertNull(domainService.markWithdrawalPaid(company, withdrawal, null));
    }

    @Test
    void reversePendingPaymentValidatesIdCreditAndIdempotency() {
        assertThrows(IllegalArgumentException.class, () -> domainService.reversePendingPayment(null, null, null));

        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.empty());
        AppException missing = assertThrows(AppException.class,
                () -> domainService.reversePendingPayment(100, null, null));
        assertSame(ErrorCode.PAYMENT_NOT_CREDITED, missing.getErrorCode());

        OrganizerWalletTransaction credit = credit(null, BigDecimal.ONE);
        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                101, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.of(credit));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.existsByPaymentIdAndType(
                101, WalletTransactionType.PAYMENT_REVERSAL)).thenReturn(true);
        assertNull(domainService.reversePendingPayment(101, null, null));
    }

    @Test
    void reversePendingPaymentRejectsCompletedExhibitionAndInsufficientBalance() {
        Exhibition completed = Exhibition.builder().status(ExhibitionStatus.COMPLETED).build();
        OrganizerWalletTransaction completedCredit = credit(completed, BigDecimal.ONE);
        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.of(completedCredit));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));

        AppException released = assertThrows(AppException.class,
                () -> domainService.reversePendingPayment(100, null, null));
        assertSame(ErrorCode.PAYMENT_ALREADY_RELEASED, released.getErrorCode());

        Exhibition active = Exhibition.builder().status(ExhibitionStatus.ACTIVE).build();
        OrganizerWalletTransaction activeCredit = credit(active, BigDecimal.TEN);
        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                101, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.of(activeCredit));
        AppException insufficient = assertThrows(AppException.class,
                () -> domainService.reversePendingPayment(101, null, null));
        assertSame(ErrorCode.INSUFFICIENT_WALLET_BALANCE, insufficient.getErrorCode());
    }

    @Test
    void reversePendingPaymentUsesDefaultReasonAndHandlesConstraintRace() {
        wallet.setPendingBalance(BigDecimal.TEN);
        OrganizerWalletTransaction credit = credit(null, BigDecimal.ONE);
        when(organizerWalletTransactionRepository.findByPaymentIdAndType(
                100, WalletTransactionType.PAYMENT_CREDIT)).thenReturn(Optional.of(credit));
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertNull(domainService.reversePendingPayment(100, null, null));
    }

    @Test
    void idempotencyGuardsHandleNullReferencesAndMissingWithdrawalId() {
        when(organizerWalletRepository.findWithLockByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(organizerWalletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(domainService.creditPendingPayment(
                company, null, null, BigDecimal.ONE, null, null));

        wallet.setAvailableBalance(BigDecimal.TEN);
        assertNotNull(domainService.reserveWithdrawal(
                company, null, BigDecimal.ONE, null));

        assertThrows(NullPointerException.class,
                () -> domainService.releaseWithdrawalReserve(company, null, null, null));

        wallet.setReservedBalance(BigDecimal.TEN);
        WithdrawalRequest withoutId = WithdrawalRequest.builder().amount(BigDecimal.ONE).build();
        assertNotNull(domainService.releaseWithdrawalReserve(
                company, withoutId, null, null));

        assertThrows(NullPointerException.class,
                () -> domainService.markWithdrawalPaid(company, null, null));
    }

    private OrganizerWalletTransaction credit(Exhibition exhibition, BigDecimal amount) {
        return OrganizerWalletTransaction.builder()
                .company(company)
                .exhibition(exhibition)
                .payment(Payment.builder().id(100).build())
                .amount(amount)
                .build();
    }
}
