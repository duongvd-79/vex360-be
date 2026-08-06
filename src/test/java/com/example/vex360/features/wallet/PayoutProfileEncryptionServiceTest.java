package com.example.vex360.features.wallet;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService;
import com.example.vex360.features.wallet.services.PayoutProfileEncryptionService.EncryptedAccountData;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

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

    @Test
    void environmentConstructorBindsConfiguredKey() {
        String key = validKey();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.wallet.encryption.active-version", "2")
                .withProperty("app.wallet.encryption.keys.2", key);

        PayoutProfileEncryptionService service = new PayoutProfileEncryptionService(environment);

        assertEquals(2, service.encryptAccountNumber("1234", null).keyVersion());
    }

    @Test
    void constructorValidatesEveryConfigurationFailure() {
        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(1, null));
        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(1, Map.of()));

        Map<Integer, String> nullValue = new HashMap<>();
        nullValue.put(1, null);
        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(1, nullValue));
        assertThrows(IllegalStateException.class,
                () -> new PayoutProfileEncryptionService(1, Map.of(1, "  ")));
        assertThrows(IllegalStateException.class,
                () -> new PayoutProfileEncryptionService(1, Map.of(1, "not-base64")));
        assertThrows(IllegalStateException.class,
                () -> new PayoutProfileEncryptionService(1,
                        Map.of(1, Base64.getEncoder().encodeToString(new byte[16]))));
        assertThrows(IllegalStateException.class,
                () -> new PayoutProfileEncryptionService(2, Map.of(1, validKey())));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void constructorAcceptsNumericStringVersionAndRejectsNonNumericVersion() {
        Map numeric = new HashMap();
        numeric.put("1", validKey());
        assertNotNull(new PayoutProfileEncryptionService(null, numeric));

        Map invalid = new HashMap();
        invalid.put("bad", validKey());
        assertThrows(IllegalStateException.class, () -> new PayoutProfileEncryptionService(1, invalid));
    }

    @Test
    void encryptValidatesInputAndNormalizesWhitespace() {
        assertThrows(IllegalArgumentException.class,
                () -> encryptionService.encryptAccountNumber(null, companyId));
        assertThrows(IllegalArgumentException.class,
                () -> encryptionService.encryptAccountNumber("   ", companyId));

        EncryptedAccountData encrypted = encryptionService.encryptAccountNumber(" 12 34 56 ", null);

        assertEquals("3456", encrypted.last4());
        assertEquals("123456", encryptionService.decryptAccountNumber(
                encrypted.ciphertextBase64(), encrypted.nonceBase64(), 1, null));
    }

    @Test
    void missingOrInvalidRuntimeKeyProducesAppException() throws Exception {
        Map<Integer, byte[]> keys = parsedKeys(encryptionService);
        keys.clear();
        AppException missing = assertThrows(AppException.class,
                () -> encryptionService.encryptAccountNumber("1234", companyId));
        assertSame(ErrorCode.ENCRYPTION_KEY_INVALID, missing.getErrorCode());

        keys.put(1, new byte[1]);
        AppException invalid = assertThrows(AppException.class,
                () -> encryptionService.encryptAccountNumber("1234", companyId));
        assertSame(ErrorCode.UNCATCHED_EXCEPTION, invalid.getErrorCode());
    }

    @Test
    void decryptHandlesNullUnknownVersionAndMalformedCiphertext() {
        assertNull(encryptionService.decryptAccountNumber(null, "nonce", companyId));
        assertNull(encryptionService.decryptAccountNumber("cipher", null, companyId));

        AppException unknown = assertThrows(AppException.class,
                () -> encryptionService.decryptAccountNumber("cipher", "nonce", 99, companyId));
        assertSame(ErrorCode.ENCRYPTION_KEY_INVALID, unknown.getErrorCode());

        AppException malformed = assertThrows(AppException.class,
                () -> encryptionService.decryptAccountNumber("not-base64", "also-bad", null, companyId));
        assertSame(ErrorCode.UNCATCHED_EXCEPTION, malformed.getErrorCode());
    }

    @Test
    void extractLast4HandlesNullShortAndLongAccounts() {
        assertEquals("", encryptionService.extractLast4(null));
        assertEquals("123", encryptionService.extractLast4(" 123 "));
        assertEquals("5678", encryptionService.extractLast4("12 34 56 78"));
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, byte[]> parsedKeys(PayoutProfileEncryptionService service) throws Exception {
        Field field = PayoutProfileEncryptionService.class.getDeclaredField("parsedKeyMap");
        field.setAccessible(true);
        return (Map<Integer, byte[]>) field.get(service);
    }

    private String validKey() {
        return Base64.getEncoder().encodeToString(
                "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
    }
}
