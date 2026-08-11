package com.example.vex360.features.wallet;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CompanyPayoutProfileServiceTest {

    @Mock
    private CompanyPayoutProfileRepository payoutProfileRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private OrganizerWalletDomainService walletDomainService;

    @InjectMocks
    private CompanyPayoutProfileService payoutProfileService;

    private User user;
    private Company company;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email("org@test.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Org Company").build();
    }

    @Test
    void getProfileForOrganizerReturnsMappedProfileWhenExists() {
        CompanyPayoutProfile profile = existingProfile(PayoutProfileStatus.VERIFIED);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.getProfileForOrganizer(user);

        assertEquals(company.getId(), response.getCompanyId());
        assertEquals("****7890", response.getAccountNumberMasked());
    }

    @Test
    void getProfileForOrganizerThrowsWhenNoProfileExists() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> payoutProfileService.getProfileForOrganizer(user));

        assertSame(ErrorCode.PAYOUT_PROFILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateProfileForOrganizerCreatesNewProfileAutoVerifiedAndInitializesWallet() {
        UpdatePayoutProfileRequestDTO dto = UpdatePayoutProfileRequestDTO.builder()
                .bankCode(" VCB ")
                .bankNameSnapshot(" Vietcombank ")
                .accountNumber(" 1234567890 ")
                .accountHolderName(" nguyen van a ")
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(payoutProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.updateProfileForOrganizer(user, dto);

        assertNotNull(response);
        assertEquals(PayoutProfileStatus.VERIFIED, response.getStatus());
        assertEquals("****7890", response.getAccountNumberMasked());
        assertEquals("1234567890", response.getAccountNumber());
        assertEquals("VCB", response.getBankCode());
        assertEquals("Vietcombank", response.getBankNameSnapshot());
        assertEquals("NGUYEN VAN A", response.getAccountHolderName());
        verify(walletDomainService).getOrCreateWallet(company);
    }

    @Test
    void updateProfileForOrganizerOverwritesExistingProfile() {
        CompanyPayoutProfile existing = existingProfile(PayoutProfileStatus.VERIFIED);
        UpdatePayoutProfileRequestDTO dto = UpdatePayoutProfileRequestDTO.builder()
                .bankCode("TCB")
                .bankNameSnapshot("Techcombank")
                .accountNumber("999888777")
                .accountHolderName("TRAN THI B")
                .build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(existing));
        when(payoutProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.updateProfileForOrganizer(user, dto);

        assertEquals(PayoutProfileStatus.VERIFIED, response.getStatus());
        assertEquals("TCB", response.getBankCode());
        assertEquals("999888777", response.getAccountNumber());
    }

    @Test
    void getProfilesForAdminFiltersByStatusWhenProvided() {
        Page<CompanyPayoutProfile> page = new PageImpl<>(java.util.List.of(existingProfile(PayoutProfileStatus.VERIFIED)));
        Pageable pageable = Pageable.unpaged();
        when(payoutProfileRepository.findByStatus(PayoutProfileStatus.VERIFIED, pageable)).thenReturn(page);

        PageResponse<CompanyPayoutProfileResponseDTO> response =
                payoutProfileService.getProfilesForAdmin(PayoutProfileStatus.VERIFIED, pageable);

        assertEquals(1, response.getContent().size());
        verify(payoutProfileRepository, never()).findAll((Pageable) any());
    }

    @Test
    void getProfilesForAdminReturnsAllWhenStatusIsNull() {
        Page<CompanyPayoutProfile> page = new PageImpl<>(java.util.List.of(existingProfile(PayoutProfileStatus.VERIFIED)));
        Pageable pageable = Pageable.unpaged();
        when(payoutProfileRepository.findAll(pageable)).thenReturn(page);

        PageResponse<CompanyPayoutProfileResponseDTO> response =
                payoutProfileService.getProfilesForAdmin(null, pageable);

        assertEquals(1, response.getContent().size());
    }

    @Test
    void getFullAccountNumberForOrganizerReturnsRawValue() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId()))
                .thenReturn(Optional.of(existingProfile(PayoutProfileStatus.VERIFIED)));

        assertEquals("1234567890", payoutProfileService.getFullAccountNumberForOrganizer(user));
    }

    @Test
    void getFullAccountNumberForOrganizerThrowsWhenMissing() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> payoutProfileService.getFullAccountNumberForOrganizer(user));

        assertSame(ErrorCode.PAYOUT_PROFILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getFullAccountNumberForAdminReturnsRawValue() {
        when(payoutProfileRepository.findByCompanyId(company.getId()))
                .thenReturn(Optional.of(existingProfile(PayoutProfileStatus.VERIFIED)));

        assertEquals("1234567890", payoutProfileService.getFullAccountNumberForAdmin(company.getId()));
    }

    @Test
    void getFullAccountNumberForAdminThrowsWhenMissing() {
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> payoutProfileService.getFullAccountNumberForAdmin(company.getId()));

        assertSame(ErrorCode.PAYOUT_PROFILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void maskingFallsBackToPlainAsterisksWhenAccountNumberTooShortOrNull() {
        CompanyPayoutProfile shortNumber = existingProfile(PayoutProfileStatus.VERIFIED);
        shortNumber.setAccountNumber("12");
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(shortNumber));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.getProfileForOrganizer(user);

        assertEquals("****", response.getAccountNumberMasked());
    }

    private CompanyPayoutProfile existingProfile(PayoutProfileStatus status) {
        return CompanyPayoutProfile.builder()
                .id(1L)
                .company(company)
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumber("1234567890")
                .accountHolderName("NGUYEN VAN A")
                .status(status)
                .build();
    }
}
