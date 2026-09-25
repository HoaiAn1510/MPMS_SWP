package com.example.manga_management.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTests {

    @Test
    void hashedPasswordVerifiesAndIsNotPlaintext() {
        String hash = PasswordHasher.hash("secret1");
        assertNotEquals("secret1", hash);
        assertTrue(PasswordHasher.isHashed(hash));
        assertTrue(PasswordHasher.matches("secret1", hash));
        assertFalse(PasswordHasher.matches("other", hash));
        assertFalse(PasswordHasher.needsUpgrade(hash));
    }

    @Test
    void legacyPlaintextStillMatchesButNeedsUpgrade() {
        assertTrue(PasswordHasher.matches("123456", "123456"));
        assertFalse(PasswordHasher.matches("654321", "123456"));
        assertTrue(PasswordHasher.needsUpgrade("123456"));
    }
}
