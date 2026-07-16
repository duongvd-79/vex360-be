package com.example.vex360.shared.utils;

import java.util.Locale;

public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
