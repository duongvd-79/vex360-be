package com.example.vex360.features.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.wallet.dtos.ReconciliationReportDTO;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.services.OrganizerWalletReconciliationService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrganizerWalletReconciliationServiceUnitTest {

    @Mock
    private OrganizerWalletRepository walletRepository;
    @Mock
    private OrganizerWalletTransactionRepository transactionRepository;
    @InjectMocks
    private OrganizerWalletReconciliationService service;

    private Company company;
    private OrganizerWallet wallet;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(UUID.randomUUID()).name("Organizer").build();
        wallet = wallet("30.00", "30.00", "0.00", "30.00");
    }

    @Test
    void reconcileWalletForCompanyCoversEveryLedgerTypeAndNullAmount() {
        when(walletRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletId(wallet.getId())).thenReturn(List.of(
                tx(WalletTransactionType.PAYMENT_CREDIT, "100.00"),
                tx(WalletTransactionType.EXHIBITION_RELEASE, "60.00"),
                tx(WalletTransactionType.PAYMENT_REVERSAL, "10.00"),
                tx(WalletTransactionType.WITHDRAWAL_RESERVED, "50.00"),
                tx(WalletTransactionType.WITHDRAWAL_RELEASED, "20.00"),
                tx(WalletTransactionType.WITHDRAWAL_PAID, "30.00"),
                OrganizerWalletTransaction.builder()
                        .type(WalletTransactionType.PAYMENT_CREDIT)
                        .amount(null)
                        .build()));

        ReconciliationReportDTO report = service.reconcileWalletForCompany(company.getId());

        assertTrue(report.isMatch());
        assertEquals(new BigDecimal("30.00"), report.getPendingLedger());
        assertEquals(new BigDecimal("30.00"), report.getAvailableLedger());
        assertEquals(new BigDecimal("0.00"), report.getReservedLedger());
        assertEquals(new BigDecimal("30.00"), report.getWithdrawnLedger());
    }

    @Test
    void reconcileWalletForCompanyReportsEachPossibleSnapshotMismatch() {
        assertFalse(reconcile(wallet("1.00", "0.00", "0.00", "0.00")).isMatch());
        assertFalse(reconcile(wallet("0.00", "1.00", "0.00", "0.00")).isMatch());
        assertFalse(reconcile(wallet("0.00", "0.00", "1.00", "0.00")).isMatch());
        assertFalse(reconcile(wallet("0.00", "0.00", "0.00", "1.00")).isMatch());
        assertTrue(reconcile(wallet("0.00", "0.00", "0.00", "0.00")).isMatch());
    }

    @Test
    void reconcileWalletForCompanyThrowsWhenWalletIsMissing() {
        when(walletRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.reconcileWalletForCompany(company.getId()));

        assertSame(ErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void reconcileAllWalletsProcessesEveryPage() {
        OrganizerWallet first = wallet("0.00", "0.00", "0.00", "0.00");
        OrganizerWallet second = wallet("0.00", "0.00", "0.00", "0.00");
        when(walletRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(
                        new PageImpl<>(List.of(first),
                                org.springframework.data.domain.PageRequest.of(0, 1), 2),
                        new PageImpl<>(List.of(second),
                                org.springframework.data.domain.PageRequest.of(1, 1), 2));
        when(transactionRepository.findByWalletId(any())).thenReturn(List.of());

        List<ReconciliationReportDTO> reports = service.reconcileAllWallets();

        assertEquals(2, reports.size());
        assertTrue(reports.stream().allMatch(ReconciliationReportDTO::isMatch));
    }

    private ReconciliationReportDTO reconcile(OrganizerWallet candidate) {
        when(walletRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(candidate));
        when(transactionRepository.findByWalletId(candidate.getId())).thenReturn(List.of());
        return service.reconcileWalletForCompany(company.getId());
    }

    private OrganizerWallet wallet(String pending, String available, String reserved, String withdrawn) {
        return OrganizerWallet.builder()
                .id(UUID.randomUUID())
                .company(company)
                .pendingBalance(new BigDecimal(pending))
                .availableBalance(new BigDecimal(available))
                .reservedBalance(new BigDecimal(reserved))
                .withdrawnTotal(new BigDecimal(withdrawn))
                .build();
    }

    private OrganizerWalletTransaction tx(WalletTransactionType type, String amount) {
        return OrganizerWalletTransaction.builder()
                .type(type)
                .amount(new BigDecimal(amount))
                .build();
    }
}
