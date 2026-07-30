package com.example.vex360.features.wallet;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;
import com.example.vex360.shared.exceptions.AppException;

class WalletKeyRotationTest {

    private final String keyV1Base64 = Base64.getEncoder()
            .encodeToString("123456789012345678901234567890v1".getBytes());
    private final String keyV2Base64 = Base64.getEncoder()
            .encodeToString("123456789012345678901234567890v2".getBytes());

    @Test
    void keyRotation_AllowsDecryptingOldVersionWithKeyring() {
        UUID companyId = UUID.randomUUID();

        // 1. Encrypt with v1
        PayoutProfileEncryptionService serviceV1 = new PayoutProfileEncryptionService(1, Map.of(1, keyV1Base64));
        EncryptedAccountData dataV1 = serviceV1.encryptAccountNumber("1234567890", companyId);
        assertEquals(1, dataV1.keyVersion());

        // 2. Rotate to v2 (keyring has v1 and v2)
        PayoutProfileEncryptionService serviceV2 = new PayoutProfileEncryptionService(2, Map.of(
                1, keyV1Base64,
                2, keyV2Base64));

        // 3. Encrypt new data with active v2
        EncryptedAccountData dataV2 = serviceV2.encryptAccountNumber("9876543210", companyId);
        assertEquals(2, dataV2.keyVersion());

        // 4. Verify serviceV2 decrypts both v1 old data and v2 new data
        String decryptedV1 = serviceV2.decryptAccountNumber(dataV1.ciphertextBase64(), dataV1.nonceBase64(),
                dataV1.keyVersion(), companyId);
        String decryptedV2 = serviceV2.decryptAccountNumber(dataV2.ciphertextBase64(), dataV2.nonceBase64(),
                dataV2.keyVersion(), companyId);

        assertEquals("1234567890", decryptedV1);
        assertEquals("9876543210", decryptedV2);
    }

    @Test
    void decryptWithUnknownOrRetiredVersion_ThrowsGenericSecurityException() {
        UUID companyId = UUID.randomUUID();
        PayoutProfileEncryptionService service = new PayoutProfileEncryptionService(1, Map.of(1, keyV1Base64));
        EncryptedAccountData data = service.encryptAccountNumber("1234567890", companyId);

        // Attempt decrypt with unknown version 99
        assertThrows(AppException.class,
                () -> service.decryptAccountNumber(data.ciphertextBase64(), data.nonceBase64(), 99, companyId));
    }

    @Test
    void startupFailFast_InvalidBase64OrKeyLength_ThrowsException() {
        // Less than 32 bytes Base64 decoded
        String shortKey = Base64.getEncoder().encodeToString("short_key".getBytes());

        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(1, Map.of(1, shortKey)));

        // Missing active key version in keyring
        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(2, Map.of(1, keyV1Base64)));
    }
}
