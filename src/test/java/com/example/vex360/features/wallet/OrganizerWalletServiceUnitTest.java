package com.example.vex360.features.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.ExhibitionWalletSummaryDTO;
import com.example.vex360.features.wallet.dtos.OrganizerWalletResponseDTO;
import com.example.vex360.features.wallet.dtos.WalletTransactionResponseDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.entities.OrganizerWallet;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.enums.WalletBucket;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.repositories.WithdrawalRequestRepository;
import com.example.vex360.features.wallet.services.OrganizerWalletService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;

@ExtendWith(MockitoExtension.class)
class OrganizerWalletServiceUnitTest {

    @Mock
    private CompanyService companyService;
    @Mock
    private OrganizerWalletRepository walletRepository;
    @Mock
    private OrganizerWalletTransactionRepository transactionRepository;
    @Mock
    private CompanyPayoutProfileRepository payoutProfileRepository;
    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock
    private ExhibitionService exhibitionService;
    @InjectMocks
    private OrganizerWalletService service;

    private User user;
    private Company company;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).name("Organizer Co").ownerUser(user).build();
        pageable = PageRequest.of(0, 20);
    }

    @Test
    void getWalletForOrganizerBuildsZeroSnapshotWhenWalletAndProfileAreMissing() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(walletRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(withdrawalRequestRepository.existsByCompanyIdAndStatusIn(any(), any())).thenReturn(false);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        OrganizerWalletResponseDTO response = service.getWalletForOrganizer(user);

        assertEquals("VND", response.getCurrency());
        assertEquals(new BigDecimal("0.00"), response.getPendingBalance());
        assertFalse(response.isHasActiveWithdrawal());
        assertNull(response.getPayoutProfileStatus());
    }

    @Test
    void getWalletForOrganizerReturnsStoredSnapshotAndProfileStatus() {
        OrganizerWallet wallet = wallet();
        CompanyPayoutProfile profile = CompanyPayoutProfile.builder()
                .company(company)
                .status(PayoutProfileStatus.VERIFIED)
                .build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(walletRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.existsByCompanyIdAndStatusIn(any(), any())).thenReturn(true);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));

        OrganizerWalletResponseDTO response = service.getWalletForOrganizer(user);

        assertTrue(response.isHasActiveWithdrawal());
        assertEquals(PayoutProfileStatus.VERIFIED, response.getPayoutProfileStatus());
        assertEquals(wallet.getAvailableBalance(), response.getAvailableBalance());
    }

    @Test
    void getTransactionsForOrganizerCoversFiltersAndEveryNullableMappingRoute() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        Exhibition exhibition = Exhibition.builder().uuid(UUID.randomUUID()).name("Expo").build();
        Company exhibitor = Company.builder().id(UUID.randomUUID()).name("Exhibitor Co").build();
        ExhibitorRegistration fullRegistration = ExhibitorRegistration.builder()
                .uuid(UUID.randomUUID())
                .company(exhibitor)
                .build();
        ExhibitorRegistration companylessRegistration = ExhibitorRegistration.builder()
                .uuid(UUID.randomUUID())
                .build();

        Payment fullPayment = Payment.builder()
                .orderCode(10L)
                .systemFee(new BigDecimal("10.00"))
                .organizerPayout(new BigDecimal("90.00"))
                .exhibitorRegistration(fullRegistration)
                .build();
        Payment companylessPayment = Payment.builder()
                .orderCode(11L)
                .exhibitorRegistration(companylessRegistration)
                .build();
        Payment registrationlessPayment = Payment.builder().orderCode(12L).build();

        List<OrganizerWalletTransaction> transactions = List.of(
                transaction(exhibition, fullPayment, "100.00"),
                transaction(exhibition, companylessPayment, "50.00"),
                transaction(null, registrationlessPayment, "25.00"),
                transaction(null, null, "5.00"));
        when(transactionRepository.findByCompanyIdAndTypeFilter(company.getId(), null, pageable))
                .thenReturn(new PageImpl<>(transactions, pageable, transactions.size()));

        PageResponse<WalletTransactionResponseDTO> response =
                service.getTransactionsForOrganizer(user, null, null, pageable);

        assertEquals(4, response.getContent().size());
        assertEquals("Exhibitor Co", response.getContent().get(0).getExhibitorCompanyName());
        assertNull(response.getContent().get(1).getExhibitorCompanyName());
        assertNull(response.getContent().get(2).getRegistrationUuid());
        assertEquals(BigDecimal.ZERO, response.getContent().get(3).getSystemFee());

        UUID exhibitionUuid = UUID.randomUUID();
        when(transactionRepository.findByCompanyIdAndExhibitionUuid(
                company.getId(), exhibitionUuid, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getTransactionsForOrganizer(user, exhibitionUuid, WalletTransactionType.PAYMENT_CREDIT, pageable);

        verify(transactionRepository).findByCompanyIdAndExhibitionUuid(
                company.getId(), exhibitionUuid, pageable);
    }

    @Test
    void getExhibitionSummariesSkipsTransactionQueryForEmptyPage() {
        when(exhibitionService.searchExhibitionsForOrganizer(
                user, null, null, null, null, null, pageable))
                .thenReturn(pageResponse(List.of(), 0));

        PageResponse<ExhibitionWalletSummaryDTO> response =
                service.getExhibitionSummariesForOrganizer(user, pageable);

        assertTrue(response.getContent().isEmpty());
        verify(transactionRepository, never()).findByExhibitionIdIn(any());
    }

    @Test
    void getExhibitionSummariesCoversAllTransactionAndPaymentVariants() {
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .name("Expo")
                .status("ACTIVE")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .build();
        when(exhibitionService.searchExhibitionsForOrganizer(
                user, null, null, null, null, null, pageable))
                .thenReturn(pageResponse(List.of(dto), 1));

        Exhibition exhibition = Exhibition.builder().id(1).build();
        Exhibition idlessExhibition = Exhibition.builder().build();
        Payment full = Payment.builder()
                .amount(new BigDecimal("200.00"))
                .systemFee(new BigDecimal("20.00"))
                .build();
        Payment nullAmounts = Payment.builder().build();
        List<OrganizerWalletTransaction> transactions = List.of(
                summaryTx(exhibition, WalletTransactionType.PAYMENT_CREDIT, "150.00", full),
                summaryTx(exhibition, WalletTransactionType.PAYMENT_CREDIT, "50.00", nullAmounts),
                summaryTx(exhibition, WalletTransactionType.PAYMENT_CREDIT, "25.00", null),
                summaryTx(exhibition, WalletTransactionType.EXHIBITION_RELEASE, "300.00", null),
                summaryTx(exhibition, WalletTransactionType.PAYMENT_REVERSAL, "10.00", null),
                summaryTx(exhibition, WalletTransactionType.WITHDRAWAL_RESERVED, "1.00", null),
                summaryTx(null, WalletTransactionType.PAYMENT_CREDIT, "1.00", null),
                summaryTx(idlessExhibition, WalletTransactionType.PAYMENT_CREDIT, "1.00", null));
        when(transactionRepository.findByExhibitionIdIn(List.of(1))).thenReturn(transactions);

        ExhibitionWalletSummaryDTO summary =
                service.getExhibitionSummariesForOrganizer(user, pageable).getContent().get(0);

        assertEquals(3, summary.getPaidRegistrationCount());
        assertEquals(new BigDecimal("275.00"), summary.getGrossRevenue());
        assertEquals(new BigDecimal("20.00"), summary.getSystemFeeTotal());
        assertEquals(new BigDecimal("225.00"), summary.getOrganizerNetRevenue());
        assertEquals(BigDecimal.ZERO, summary.getPendingBalance());
        assertEquals(new BigDecimal("300.00"), summary.getAvailableBalance());
        assertEquals(new BigDecimal("10.00"), summary.getReversedAmount());
        assertEquals(ExhibitionStatus.ACTIVE, summary.getStatus());
    }

    private OrganizerWallet wallet() {
        return OrganizerWallet.builder()
                .company(company)
                .currency("VND")
                .pendingBalance(new BigDecimal("1.00"))
                .availableBalance(new BigDecimal("2.00"))
                .reservedBalance(new BigDecimal("3.00"))
                .withdrawnTotal(new BigDecimal("4.00"))
                .build();
    }

    private OrganizerWalletTransaction transaction(
            Exhibition exhibition, Payment payment, String amount) {
        return OrganizerWalletTransaction.builder()
                .uuid(UUID.randomUUID())
                .exhibition(exhibition)
                .payment(payment)
                .amount(new BigDecimal(amount))
                .type(WalletTransactionType.PAYMENT_CREDIT)
                .fromBucket(WalletBucket.VOID)
                .toBucket(WalletBucket.PENDING)
                .pendingAfter(BigDecimal.ONE)
                .availableAfter(BigDecimal.ONE)
                .reservedAfter(BigDecimal.ONE)
                .withdrawnAfter(BigDecimal.ONE)
                .reason("reason")
                .build();
    }

    private OrganizerWalletTransaction summaryTx(
            Exhibition exhibition, WalletTransactionType type, String amount, Payment payment) {
        return OrganizerWalletTransaction.builder()
                .exhibition(exhibition)
                .type(type)
                .amount(new BigDecimal(amount))
                .payment(payment)
                .build();
    }

    private PageResponse<ExhibitionResponseDTO> pageResponse(
            List<ExhibitionResponseDTO> content, long total) {
        return PageResponse.<ExhibitionResponseDTO>builder()
                .content(content)
                .page(0)
                .size(20)
                .totalElements(total)
                .totalPages(total == 0 ? 0 : 1)
                .first(true)
                .last(true)
                .build();
    }
}
