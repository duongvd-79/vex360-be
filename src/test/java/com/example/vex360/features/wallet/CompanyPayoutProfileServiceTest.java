package com.example.vex360.features.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;

@ExtendWith(MockitoExtension.class)
class CompanyPayoutProfileServiceTest {

    @Mock
    private CompanyPayoutProfileRepository payoutProfileRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private PayoutProfileEncryptionService encryptionService;

    @InjectMocks
    private CompanyPayoutProfileService payoutProfileService;

    private User user;
    private User adminUser;
    private Company company;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email("org@test.com").build();
        adminUser = User.builder().id(UUID.randomUUID()).email("admin@test.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Org Company").build();
    }

    @Test
    void updateProfileForOrganizer_Success() {
        UpdatePayoutProfileRequestDTO dto = UpdatePayoutProfileRequestDTO.builder()
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumber("1234567890")
                .accountHolderName("NGUYEN VAN A")
                .build();

        EncryptedAccountData encrypted = new EncryptedAccountData("cipher", "nonce", 1, "7890");

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(encryptionService.encryptAccountNumber("1234567890", company.getId())).thenReturn(encrypted);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(payoutProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.updateProfileForOrganizer(user, dto);

        assertNotNull(response);
        assertEquals(PayoutProfileStatus.PENDING_VERIFICATION, response.getStatus());
        assertEquals("****7890", response.getAccountNumberMasked());
        assertEquals("VCB", response.getBankCode());
        assertEquals("NGUYEN VAN A", response.getAccountHolderName());
    }

    @Test
    void verifyProfileForAdmin_Success() {
        CompanyPayoutProfile profile = CompanyPayoutProfile.builder()
                .company(company)
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumberCiphertext("cipher")
                .accountNumberNonce("nonce")
                .encryptionKeyVersion(1)
                .accountNumberLast4("7890")
                .accountHolderName("NGUYEN VAN A")
                .status(PayoutProfileStatus.PENDING_VERIFICATION)
                .build();

        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));
        when(payoutProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.verifyProfileForAdmin(company.getId(),
                adminUser);

        assertNotNull(response);
        assertEquals(PayoutProfileStatus.VERIFIED, response.getStatus());
        verify(payoutProfileRepository).save(profile);
    }

    @Test
    void getProfileForOrganizerReturnsMappedProfile() {
        CompanyPayoutProfile profile = profile();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));

        CompanyPayoutProfileResponseDTO response = payoutProfileService.getProfileForOrganizer(user);

        assertEquals(company.getId(), response.getCompanyId());
        assertEquals(company.getName(), response.getCompanyName());
        assertEquals("****7890", response.getAccountNumberMasked());
    }

    @Test
    void getProfileForOrganizerThrowsWhenProfileDoesNotExist() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> payoutProfileService.getProfileForOrganizer(user));
    }

    @Test
    void updateExistingProfileTrimsValuesAndClearsReviewState() {
        CompanyPayoutProfile profile = profile();
        profile.setStatus(PayoutProfileStatus.REJECTED);
        profile.setVerifiedBy(adminUser);
        profile.setRejectedBy(adminUser);
        profile.setRejectedReason("old reason");
        UpdatePayoutProfileRequestDTO dto = UpdatePayoutProfileRequestDTO.builder()
                .bankCode(" VCB ")
                .bankNameSnapshot(" Vietcombank ")
                .accountNumber("1234567890")
                .accountHolderName(" nguyen van a ")
                .build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(encryptionService.encryptAccountNumber("1234567890", company.getId()))
                .thenReturn(new EncryptedAccountData("new-cipher", "new-nonce", 2, "7890"));
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));
        when(payoutProfileRepository.save(profile)).thenReturn(profile);

        payoutProfileService.updateProfileForOrganizer(user, dto);

        assertEquals("VCB", profile.getBankCode());
        assertEquals("Vietcombank", profile.getBankNameSnapshot());
        assertEquals("NGUYEN VAN A", profile.getAccountHolderName());
        assertEquals(PayoutProfileStatus.PENDING_VERIFICATION, profile.getStatus());
        assertNull(profile.getVerifiedBy());
        assertNull(profile.getRejectedBy());
        assertNull(profile.getRejectedReason());
    }

    @Test
    void getProfilesForAdminSupportsStatusAndAllProfiles() {
        CompanyPayoutProfile profile = profile();
        PageRequest pageable = PageRequest.of(0, 10);
        PageImpl<CompanyPayoutProfile> page = new PageImpl<>(List.of(profile), pageable, 1);
        when(payoutProfileRepository.findByStatus(PayoutProfileStatus.VERIFIED, pageable)).thenReturn(page);
        when(payoutProfileRepository.findAll(pageable)).thenReturn(page);

        PageResponse<CompanyPayoutProfileResponseDTO> filtered =
                payoutProfileService.getProfilesForAdmin(PayoutProfileStatus.VERIFIED, pageable);
        PageResponse<CompanyPayoutProfileResponseDTO> all =
                payoutProfileService.getProfilesForAdmin(null, pageable);

        assertEquals(1, filtered.getContent().size());
        assertEquals(1, all.getContent().size());
    }

    @Test
    void rejectProfileSupportsExplicitAndDefaultReasons() {
        CompanyPayoutProfile profile = profile();
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));
        when(payoutProfileRepository.save(profile)).thenReturn(profile);

        CompanyPayoutProfileResponseDTO explicit =
                payoutProfileService.rejectProfileForAdmin(company.getId(), " invalid account ", adminUser);
        assertEquals("invalid account", explicit.getRejectedReason());
        assertEquals(PayoutProfileStatus.REJECTED, explicit.getStatus());
        assertNull(profile.getVerifiedBy());

        CompanyPayoutProfileResponseDTO defaultReason =
                payoutProfileService.rejectProfileForAdmin(company.getId(), null, adminUser);
        assertEquals("Rejected by admin", defaultReason.getRejectedReason());
    }

    @Test
    void decryptAccountNumberForAdminDelegatesToEncryptionService() {
        CompanyPayoutProfile profile = profile();
        when(payoutProfileRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(profile));
        when(encryptionService.decryptAccountNumber("cipher", "nonce", company.getId()))
                .thenReturn("1234567890");

        assertEquals("1234567890",
                payoutProfileService.decryptAccountNumberForAdmin(company.getId(), adminUser));
    }

    private CompanyPayoutProfile profile() {
        return CompanyPayoutProfile.builder()
                .company(company)
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .accountNumberCiphertext("cipher")
                .accountNumberNonce("nonce")
                .encryptionKeyVersion(1)
                .accountNumberLast4("7890")
                .accountHolderName("NGUYEN VAN A")
                .status(PayoutProfileStatus.PENDING_VERIFICATION)
                .build();
    }
}
