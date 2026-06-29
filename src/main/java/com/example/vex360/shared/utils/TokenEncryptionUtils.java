package com.example.vex360.shared.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import jakarta.annotation.PostConstruct;

public class TokenEncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static byte[] keyBytes = "Vex360SecretKey_".getBytes(StandardCharsets.UTF_8);

    @Component
    public static class KeyInitializer {
        @Value("${app.security.token-encryption-key}")
        private String tokenEncryptionKey;

        @PostConstruct
        public void init() {
            setKey(tokenEncryptionKey);
        }
    }

    public static void setKey(String secretKey) {
        if (secretKey != null) {
            byte[] bytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 16 || bytes.length == 24 || bytes.length == 32) {
                keyBytes = bytes;
            } else if (bytes.length > 16) {
                int newLength = bytes.length >= 32 ? 32 : (bytes.length >= 24 ? 24 : 16);
                byte[] temp = new byte[newLength];
                System.arraycopy(bytes, 0, temp, 0, newLength);
                keyBytes = temp;
            } else {
                byte[] temp = new byte[16];
                System.arraycopy(bytes, 0, temp, 0, bytes.length);
                keyBytes = temp;
            }
        }
    }

    public static String encrypt(String value) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public static String decrypt(String encryptedValue) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getUrlDecoder().decode(encryptedValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If the token is modified, expired, or malformed, throw authentication error
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
