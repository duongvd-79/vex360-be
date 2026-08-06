package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.example.vex360.shared.exceptions.AppException;

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
}
