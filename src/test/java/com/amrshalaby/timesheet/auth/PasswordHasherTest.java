package com.amrshalaby.timesheet.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndVerifiesPassword() {
        String hash = hasher.hash("a-good-temporary-password");

        assertTrue(hasher.matches("a-good-temporary-password", hash));
        assertFalse(hasher.matches("wrong-password", hash));
    }

    @Test
    void includesRandomSalt() {
        String first = hasher.hash("a-good-temporary-password");
        String second = hasher.hash("a-good-temporary-password");

        assertNotEquals(first, second);
    }

    @Test
    void rejectsShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("short"));
    }
}
