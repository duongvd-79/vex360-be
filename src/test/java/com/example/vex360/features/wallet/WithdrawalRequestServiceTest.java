package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class WithdrawalRequestServiceTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private CompanyPayoutProfileRepository payoutProfileRepository;

    @Mock
    private OrganizerWalletDomainService walletDomainService;

    @InjectMocks
    private WithdrawalRequestService withdrawalRequestService;

    private User user;
    private User adminUser;
    private Company company;
    private CompanyPayoutProfile payoutProfile;
    private OrganizerWallet wallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email("org@test.com").build();
        adminUser = User.builder().id(UUID.randomUUID()).email("admin@test.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Company A").build();

        payoutProfile = CompanyPayoutProfile.builder()
                .id(1L)
                .company(company)
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumber("1234567890")
                .accountHolderName("NGUYEN VAN A")
                .status(PayoutProfileStatus.VERIFIED)
                .build();

        wallet = OrganizerWallet.builder()
                .id(UUID.randomUUID())
                .company(company)
                .availableBalance(new BigDecimal("1000000.00"))
                .reservedBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void createWithdrawalRequest_Success() {
        CreateWithdrawalRequestDTO dto = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("500000.00"))
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(payoutProfile));
        when(withdrawalRequestRepository.existsByCompanyIdAndStatusIn(eq(company.getId()), any())).thenReturn(false);
        when(walletDomainService.getWalletWithLock(company)).thenReturn(wallet);
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponseDTO response = withdrawalRequestService.createWithdrawalRequest(user, dto);

        assertNotNull(response);
        assertEquals(WithdrawalStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("500000.00"), response.getAmount());
        assertEquals("VCB", response.getBankCodeSnapshot());
        verify(walletDomainService).reserveWithdrawal(eq(company), any(), eq(new BigDecimal("500000.00")), eq(user));
    }

    @Test
    void createWithdrawalRequest_UnverifiedProfile_ThrowsException() {
        payoutProfile.setStatus(PayoutProfileStatus.PENDING_VERIFICATION);
        CreateWithdrawalRequestDTO dto = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("500000.00"))
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(payoutProfile));

        assertThrows(AppException.class, () -> withdrawalRequestService.createWithdrawalRequest(user, dto));
    }

    @Test
    void createWithdrawalRequest_ActiveWithdrawalExists_ThrowsException() {
        CreateWithdrawalRequestDTO dto = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("500000.00"))
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(payoutProfile));
        when(withdrawalRequestRepository.existsByCompanyIdAndStatusIn(eq(company.getId()), any())).thenReturn(true);

        assertThrows(AppException.class, () -> withdrawalRequestService.createWithdrawalRequest(user, dto));
    }

    @Test
    void cancelWithdrawalRequest_PendingStatus_Success() {
        UUID reqUuid = UUID.randomUUID();
        WithdrawalRequest req = WithdrawalRequest.builder()
                .uuid(reqUuid)
                .company(company)
                .amount(new BigDecimal("500000.00"))
                .status(WithdrawalStatus.PENDING)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(withdrawalRequestRepository.findWithLockByUuid(reqUuid)).thenReturn(Optional.of(req));
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponseDTO response = withdrawalRequestService.cancelWithdrawalRequestForOrganizer(user,
                reqUuid);

        assertNotNull(response);
        assertEquals(WithdrawalStatus.CANCELED, response.getStatus());
        verify(walletDomainService).releaseWithdrawalReserve(eq(company), eq(req), any(), eq(user));
    }

    @Test
    void markPaidWithdrawalRequest_ApprovedStatus_Success() {
        UUID reqUuid = UUID.randomUUID();
        WithdrawalRequest req = WithdrawalRequest.builder()
                .uuid(reqUuid)
                .company(company)
                .amount(new BigDecimal("500000.00"))
                .status(WithdrawalStatus.APPROVED)
                .build();

        AdminMarkPaidWithdrawalRequestDTO dto = AdminMarkPaidWithdrawalRequestDTO.builder()
                .transferReference("FT12345678")
                .proofUrl("https://cloudinary.com/proof.jpg")
                .build();

        when(withdrawalRequestRepository.findWithLockByUuid(reqUuid)).thenReturn(Optional.of(req));
        when(withdrawalRequestRepository.existsByTransferReference("FT12345678")).thenReturn(false);
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponseDTO response = withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser,
                reqUuid, dto);

        assertNotNull(response);
        assertEquals(WithdrawalStatus.PAID, response.getStatus());
        assertEquals("FT12345678", response.getTransferReference());
        verify(walletDomainService).markWithdrawalPaid(company, req, adminUser);
    }

    @Test
    void createWithdrawalRequestRejectsMissingProfile() {
        CreateWithdrawalRequestDTO dto = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("500000.00"))
                .build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> withdrawalRequestService.createWithdrawalRequest(user, dto));

        assertSame(ErrorCode.PAYOUT_PROFILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createWithdrawalRequestRejectsBelowMinimumAndInsufficientBalance() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(payoutProfile));
        when(walletDomainService.getWalletWithLock(company)).thenReturn(wallet);
        when(withdrawalRequestRepository.existsByCompanyIdAndStatusIn(eq(company.getId()), any()))
                .thenReturn(false);

        CreateWithdrawalRequestDTO belowMinimum = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("99999.994"))
                .build();
        AppException minimum = assertThrows(AppException.class,
                () -> withdrawalRequestService.createWithdrawalRequest(user, belowMinimum));
        assertSame(ErrorCode.WITHDRAWAL_AMOUNT_BELOW_MINIMUM, minimum.getErrorCode());

        CreateWithdrawalRequestDTO excessive = CreateWithdrawalRequestDTO.builder()
                .amount(new BigDecimal("1000000.01"))
                .build();
        AppException insufficient = assertThrows(AppException.class,
                () -> withdrawalRequestService.createWithdrawalRequest(user, excessive));
        assertSame(ErrorCode.INSUFFICIENT_WALLET_BALANCE, insufficient.getErrorCode());
    }

    @Test
    void organizerQueriesMapPageAndEnforceCompanyOwnership() {
        UUID uuid = UUID.randomUUID();
        WithdrawalRequest own = request(uuid, company, WithdrawalStatus.PENDING);
        PageRequest pageable = PageRequest.of(0, 10);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(withdrawalRequestRepository.findByCompanyId(company.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(own), pageable, 1));
        when(withdrawalRequestRepository.findByUuid(uuid)).thenReturn(Optional.of(own));

        PageResponse<WithdrawalRequestResponseDTO> page =
                withdrawalRequestService.getWithdrawalRequestsForOrganizer(user, pageable);
        WithdrawalRequestResponseDTO details =
                withdrawalRequestService.getWithdrawalRequestDetailsForOrganizer(user, uuid);

        assertEquals(1, page.getContent().size());
        assertEquals(uuid, details.getUuid());

        Company other = Company.builder().id(UUID.randomUUID()).build();
        WithdrawalRequest foreign = request(uuid, other, WithdrawalStatus.PENDING);
        when(withdrawalRequestRepository.findByUuid(uuid)).thenReturn(Optional.of(foreign));
        AppException hidden = assertThrows(AppException.class,
                () -> withdrawalRequestService.getWithdrawalRequestDetailsForOrganizer(user, uuid));
        assertSame(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND, hidden.getErrorCode());
    }

    @Test
    void organizerDetailAndCancelHandleMissingOrInvalidRequests() {
        UUID uuid = UUID.randomUUID();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(withdrawalRequestRepository.findByUuid(uuid)).thenReturn(Optional.empty());
        assertThrows(AppException.class,
                () -> withdrawalRequestService.getWithdrawalRequestDetailsForOrganizer(user, uuid));

        Company other = Company.builder().id(UUID.randomUUID()).build();
        WithdrawalRequest foreign = request(uuid, other, WithdrawalStatus.PENDING);
        WithdrawalRequest approved = request(uuid, company, WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findWithLockByUuid(uuid))
                .thenReturn(Optional.empty(), Optional.of(foreign), Optional.of(approved));

        assertThrows(AppException.class,
                () -> withdrawalRequestService.cancelWithdrawalRequestForOrganizer(user, uuid));
        assertThrows(AppException.class,
                () -> withdrawalRequestService.cancelWithdrawalRequestForOrganizer(user, uuid));
        AppException invalid = assertThrows(AppException.class,
                () -> withdrawalRequestService.cancelWithdrawalRequestForOrganizer(user, uuid));
        assertSame(ErrorCode.INVALID_WITHDRAWAL_TRANSITION, invalid.getErrorCode());
    }

    @Test
    void adminQueriesReturnPageAndDetails() {
        UUID uuid = UUID.randomUUID();
        WithdrawalRequest request = request(uuid, company, WithdrawalStatus.PENDING);
        PageRequest pageable = PageRequest.of(0, 10);
        when(withdrawalRequestRepository.findByAdminFilter(
                WithdrawalStatus.PENDING, company.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(request), pageable, 1));
        when(withdrawalRequestRepository.findByUuid(uuid)).thenReturn(Optional.of(request));

        assertEquals(1, withdrawalRequestService.getWithdrawalRequestsForAdmin(
                WithdrawalStatus.PENDING, company.getId(), pageable).getContent().size());
        assertEquals(uuid, withdrawalRequestService.getWithdrawalRequestDetailsForAdmin(uuid).getUuid());
    }

    @Test
    void approveWithdrawalRequestHandlesSuccessMissingAndInvalidStatus() {
        UUID uuid = UUID.randomUUID();
        WithdrawalRequest pending = request(uuid, company, WithdrawalStatus.PENDING);
        WithdrawalRequest paid = request(uuid, company, WithdrawalStatus.PAID);
        when(withdrawalRequestRepository.findWithLockByUuid(uuid))
                .thenReturn(Optional.of(pending), Optional.empty(), Optional.of(paid));
        when(withdrawalRequestRepository.save(pending)).thenReturn(pending);

        WithdrawalRequestResponseDTO approved =
                withdrawalRequestService.approveWithdrawalRequestForAdmin(adminUser, uuid);
        assertEquals(WithdrawalStatus.APPROVED, approved.getStatus());
        assertEquals(adminUser, pending.getApprovedBy());
        assertNotNull(pending.getApprovedAt());

        assertThrows(AppException.class,
                () -> withdrawalRequestService.approveWithdrawalRequestForAdmin(adminUser, uuid));
        assertThrows(AppException.class,
                () -> withdrawalRequestService.approveWithdrawalRequestForAdmin(adminUser, uuid));
    }

    @Test
    void rejectWithdrawalRequestSupportsPendingApprovedAndRejectsFinalStatus() {
        UUID uuid = UUID.randomUUID();
        WithdrawalRequest pending = request(uuid, company, WithdrawalStatus.PENDING);
        WithdrawalRequest approved = request(uuid, company, WithdrawalStatus.APPROVED);
        WithdrawalRequest paid = request(uuid, company, WithdrawalStatus.PAID);
        when(withdrawalRequestRepository.findWithLockByUuid(uuid))
                .thenReturn(Optional.of(pending), Optional.of(approved), Optional.of(paid));
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponseDTO explicit =
                withdrawalRequestService.rejectWithdrawalRequestForAdmin(adminUser, uuid, " invalid ");
        assertEquals("invalid", explicit.getRejectedReason());

        WithdrawalRequestResponseDTO fallback =
                withdrawalRequestService.rejectWithdrawalRequestForAdmin(adminUser, uuid, null);
        assertEquals("Rejected by admin", fallback.getRejectedReason());

        AppException invalid = assertThrows(AppException.class,
                () -> withdrawalRequestService.rejectWithdrawalRequestForAdmin(adminUser, uuid, null));
        assertSame(ErrorCode.INVALID_WITHDRAWAL_TRANSITION, invalid.getErrorCode());
    }

    @Test
    void markPaidValidatesStatusAndUniqueTransferReference() {
        UUID uuid = UUID.randomUUID();
        AdminMarkPaidWithdrawalRequestDTO dto = AdminMarkPaidWithdrawalRequestDTO.builder()
                .transferReference(" REF ")
                .build();
        WithdrawalRequest pending = request(uuid, company, WithdrawalStatus.PENDING);
        WithdrawalRequest approved = request(uuid, company, WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findWithLockByUuid(uuid))
                .thenReturn(Optional.empty(), Optional.of(pending), Optional.of(approved));

        assertThrows(AppException.class,
                () -> withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser, uuid, dto));
        assertThrows(AppException.class,
                () -> withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser, uuid, dto));

        when(withdrawalRequestRepository.existsByTransferReference("REF")).thenReturn(true);
        AppException duplicate = assertThrows(AppException.class,
                () -> withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser, uuid, dto));
        assertSame(ErrorCode.TRANSFER_REFERENCE_DUPLICATED, duplicate.getErrorCode());
    }

    @Test
    void markPaidIgnoresNullAndBlankProofUrls() {
        UUID uuid = UUID.randomUUID();
        WithdrawalRequest first = request(uuid, company, WithdrawalStatus.APPROVED);
        WithdrawalRequest second = request(uuid, company, WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findWithLockByUuid(uuid))
                .thenReturn(Optional.of(first), Optional.of(second));
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminMarkPaidWithdrawalRequestDTO noProof = AdminMarkPaidWithdrawalRequestDTO.builder()
                .transferReference("REF-1")
                .proofUrl(null)
                .build();
        AdminMarkPaidWithdrawalRequestDTO blankProof = AdminMarkPaidWithdrawalRequestDTO.builder()
                .transferReference("REF-2")
                .proofUrl("   ")
                .build();

        withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser, uuid, noProof);
        withdrawalRequestService.markPaidWithdrawalRequestForAdmin(adminUser, uuid, blankProof);

        assertEquals(null, first.getProofUrl());
        assertEquals(null, second.getProofUrl());
    }

    @Test
    void proofReferenceCheckDelegatesToRepository() {
        when(withdrawalRequestRepository.existsByProofUrlContaining("public-id")).thenReturn(true);

        assertEquals(true, withdrawalRequestService.isProofAssetReferenced("public-id"));
    }

    private WithdrawalRequest request(UUID uuid, Company owner, WithdrawalStatus status) {
        return WithdrawalRequest.builder()
                .uuid(uuid)
                .company(owner)
                .wallet(wallet)
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .minimumAmountSnapshot(WithdrawalRequestService.MINIMUM_WITHDRAWAL_AMOUNT)
                .status(status)
                .bankCodeSnapshot("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumberSnapshot("0000001234")
                .accountHolderNameSnapshot("NGUYEN VAN A")
                .build();
    }
}
