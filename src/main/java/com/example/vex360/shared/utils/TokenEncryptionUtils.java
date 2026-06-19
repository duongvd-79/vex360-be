package com.example.vex360.shared.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

public class TokenEncryptionUtils {

    private static final String ALGORITHM = "AES";
    // AES requires a 16-byte key for 128-bit key length
    private static final byte[] KEY = "Vex360SecretKey_".getBytes(StandardCharsets.UTF_8);

    public static String encrypt(String value) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
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
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
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
