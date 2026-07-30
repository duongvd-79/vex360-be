package com.example.vex360.features.wallet.services;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PayoutProfileEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final Integer activeKeyVersion;
    private final Map<Integer, String> configuredKeyStrings;
    private final Map<Integer, byte[]> parsedKeyMap = new HashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PayoutProfileEncryptionService(Environment environment) {
        this(
                environment.getProperty("app.wallet.encryption.active-version", Integer.class, 1),
                Binder.get(environment)
                        .bind("app.wallet.encryption.keys", Bindable.mapOf(Integer.class, String.class))
                        .orElse(Map.of()));
    }

    public PayoutProfileEncryptionService(Integer activeKeyVersion, Map<Integer, String> configuredKeyStrings) {
        this.activeKeyVersion = activeKeyVersion != null ? activeKeyVersion : 1;
        this.configuredKeyStrings = configuredKeyStrings;
        init();
    }

    private void init() {
        if (configuredKeyStrings == null || configuredKeyStrings.isEmpty()) {
            throw new IllegalStateException("Wallet encryption key configuration is missing or empty.");
        }

        for (Map.Entry<?, String> entry : configuredKeyStrings.entrySet()) {
            Object keyObj = entry.getKey();
            Integer version;
            try {
                version = keyObj instanceof Integer i ? i : Integer.parseInt(keyObj.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Wallet encryption key version '" + keyObj + "' must be an integer.");
            }

            String keyStr = entry.getValue();
            if (keyStr == null || keyStr.trim().isEmpty()) {
                throw new IllegalStateException("Wallet encryption key for version " + version + " is null or empty.");
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(keyStr.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Wallet encryption key for version " + version + " is not valid Base64.");
            }

            if (decoded.length != 32) {
                throw new IllegalStateException("Wallet encryption key for version " + version
                        + " must be exactly 32 bytes (256 bits), found: " + decoded.length + " bytes.");
            }

            parsedKeyMap.put(version, decoded);
        }

        if (!parsedKeyMap.containsKey(activeKeyVersion)) {
            throw new IllegalStateException(
                    "Wallet encryption active key version " + activeKeyVersion + " is not present in key map.");
        }
    }

    public EncryptedAccountData encryptAccountNumber(String plainAccountNumber, UUID companyId) {
        if (plainAccountNumber == null || plainAccountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        String normalized = plainAccountNumber.trim().replaceAll("\\s+", "");

        byte[] activeKeyBytes = parsedKeyMap.get(activeKeyVersion);
        if (activeKeyBytes == null) {
            throw new AppException(ErrorCode.ENCRYPTION_KEY_INVALID);
        }

        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            SecretKey key = new SecretKeySpec(activeKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            if (companyId != null) {
                cipher.updateAAD(companyId.toString().getBytes(StandardCharsets.UTF_8));
            }

            byte[] cipherText = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            String last4 = extractLast4(normalized);

            return new EncryptedAccountData(
                    Base64.getEncoder().encodeToString(cipherText),
                    Base64.getEncoder().encodeToString(nonce),
                    activeKeyVersion,
                    last4);
        } catch (Exception e) {
            log.error("Failed to encrypt payout account number", e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public String decryptAccountNumber(String ciphertextBase64, String nonceBase64, UUID companyId) {
        return decryptAccountNumber(ciphertextBase64, nonceBase64, activeKeyVersion, companyId);
    }

    public String decryptAccountNumber(String ciphertextBase64, String nonceBase64, Integer keyVersion,
            UUID companyId) {
        if (ciphertextBase64 == null || nonceBase64 == null) {
            return null;
        }

        Integer versionToUse = keyVersion != null ? keyVersion : activeKeyVersion;
        byte[] keyBytes = parsedKeyMap.get(versionToUse);

        if (keyBytes == null) {
            log.warn("Attempted to decrypt with unknown or retired key version: {}", versionToUse);
            throw new AppException(ErrorCode.ENCRYPTION_KEY_INVALID);
        }

        try {
            byte[] cipherText = Base64.getDecoder().decode(ciphertextBase64);
            byte[] nonce = Base64.getDecoder().decode(nonceBase64);

            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            if (companyId != null) {
                cipher.updateAAD(companyId.toString().getBytes(StandardCharsets.UTF_8));
            }

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt payout account number with key version {}", versionToUse, e);
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public String extractLast4(String rawAccount) {
        if (rawAccount == null)
            return "";
        String clean = rawAccount.trim().replaceAll("\\s+", "");
        if (clean.length() <= 4)
            return clean;
        return clean.substring(clean.length() - 4);
    }

    public record EncryptedAccountData(
            String ciphertextBase64,
            String nonceBase64,
            Integer keyVersion,
            String last4) {
    }
}
