package com.example.vex360.features.wallet;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.controllers.AdminWalletController;
import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.repositories.WithdrawalRequestRepository;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.enums.Role;

@ExtendWith(MockitoExtension.class)
class WithdrawalAccountAccessTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    private PayoutProfileEncryptionService encryptionService;

    @InjectMocks
    private WithdrawalRequestService withdrawalRequestService;

    @Mock
    private AdminWalletController adminWalletController;

    private User adminUser;
    private Company company;
    private UUID withdrawalUuid;

    @BeforeEach
    void setUp() {
        String base64Key = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
        encryptionService = new PayoutProfileEncryptionService(1, Map.of(1, base64Key));

        adminUser = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).email("admin@vex360.com").build();
        company = Company.builder().id(UUID.randomUUID()).name("Test Organizer").build();
        withdrawalUuid = UUID.randomUUID();
    }

    @Test
    void decryptWithdrawalAccount_UsesSnapshottedDetails_AndIncludesNoStoreHeader() {
        // Encrypt snapshot account "999988887777"
        PayoutProfileEncryptionService.EncryptedAccountData data = encryptionService
                .encryptAccountNumber("999988887777", company.getId());

        WithdrawalRequest request = WithdrawalRequest.builder()
                .uuid(withdrawalUuid)
                .company(company)
                .accountNumberCiphertextSnapshot(data.ciphertextBase64())
                .accountNumberNonceSnapshot(data.nonceBase64())
                .encryptionKeyVersionSnapshot(data.keyVersion())
                .build();

        when(withdrawalRequestRepository.findByUuid(withdrawalUuid)).thenReturn(Optional.of(request));

        // Inject encryption service manually into service
        ReflectionTestUtils.setField(withdrawalRequestService, "encryptionService",
                encryptionService);

        String decrypted = withdrawalRequestService.decryptWithdrawalAccountNumberForAdmin(withdrawalUuid, adminUser);
        assertEquals("999988887777", decrypted);

        // Verify controller header
        AdminWalletController controller = new AdminWalletController(
                null, withdrawalRequestService, null, null, null);

        CustomUserDetails userDetails = new CustomUserDetails(adminUser);
        ResponseEntity<ApiResponse<Map<String, String>>> response = controller
                .getDecryptedWithdrawalAccount(userDetails, withdrawalUuid);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("999988887777", response.getBody().data().get("accountNumber"));
    }
}
