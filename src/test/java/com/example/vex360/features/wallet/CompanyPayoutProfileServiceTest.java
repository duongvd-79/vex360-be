package com.example.vex360.features.wallet;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;

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
    private Company company;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email("org@test.com").build();
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
        assertEquals(PayoutProfileStatus.VERIFIED, response.getStatus());
        assertEquals("****7890", response.getAccountNumberMasked());
        assertEquals("VCB", response.getBankCode());
        assertEquals("NGUYEN VAN A", response.getAccountHolderName());
    }
}
