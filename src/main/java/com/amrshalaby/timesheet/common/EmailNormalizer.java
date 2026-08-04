package com.amrshalaby.timesheet.common;

import java.util.Locale;

public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        return normalized;
    }
}
