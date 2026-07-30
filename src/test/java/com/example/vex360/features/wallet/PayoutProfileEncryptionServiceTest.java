package com.example.vex360.features.wallet;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;
import com.example.vex360.shared.exceptions.AppException;

class PayoutProfileEncryptionServiceTest {

    private PayoutProfileEncryptionService encryptionService;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        String base64Key = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
        encryptionService = new PayoutProfileEncryptionService(1, Map.of(1, base64Key));
        companyId = UUID.randomUUID();
    }

    @Test
    void encryptAndDecrypt_Success() {
        String originalAccount = "123456789012";
        EncryptedAccountData encrypted = encryptionService.encryptAccountNumber(originalAccount, companyId);

        assertNotNull(encrypted);
        assertNotNull(encrypted.ciphertextBase64());
        assertNotNull(encrypted.nonceBase64());
        assertEquals("9012", encrypted.last4());

        String decrypted = encryptionService.decryptAccountNumber(encrypted.ciphertextBase64(),
                encrypted.nonceBase64(), companyId);
        assertEquals(originalAccount, decrypted);
    }

    @Test
    void decryptWithWrongCompanyId_ThrowsException() {
        String originalAccount = "123456789012";
        EncryptedAccountData encrypted = encryptionService.encryptAccountNumber(originalAccount, companyId);

        UUID wrongCompanyId = UUID.randomUUID();
        assertThrows(AppException.class, () -> encryptionService.decryptAccountNumber(encrypted.ciphertextBase64(),
                encrypted.nonceBase64(), wrongCompanyId));
    }
}
