package com.example.vex360.shared.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RandomPasswordGenerator {

    private static final int GENERATED_PASSWORD_LENGTH = 8;
    private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_CHARS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*";
    private static final String PASSWORD_CHARS = UPPERCASE_CHARS + LOWERCASE_CHARS + DIGIT_CHARS + SPECIAL_CHARS;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomPasswordGenerator() {
    }

    public static String generate() {
        List<Character> passwordChars = new ArrayList<>(GENERATED_PASSWORD_LENGTH);
        passwordChars.add(randomChar(UPPERCASE_CHARS));
        passwordChars.add(randomChar(LOWERCASE_CHARS));
        passwordChars.add(randomChar(DIGIT_CHARS));
        passwordChars.add(randomChar(SPECIAL_CHARS));

        while (passwordChars.size() < GENERATED_PASSWORD_LENGTH) {
            passwordChars.add(randomChar(PASSWORD_CHARS));
        }

        Collections.shuffle(passwordChars, SECURE_RANDOM);

        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (Character passwordChar : passwordChars) {
            password.append(passwordChar);
        }
        return password.toString();
    }

    private static char randomChar(String source) {
        return source.charAt(SECURE_RANDOM.nextInt(source.length()));
    }
}
