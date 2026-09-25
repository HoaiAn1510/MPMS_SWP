package com.example.manga_management.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OtpServiceTests {

    private final OtpService service = new OtpService();

    @Test
    void correctOtpIsAcceptedOnceOnly() {
        String otp = service.generateOtp("k");
        assertTrue(service.verifyOtp("k", otp));
        assertFalse(service.verifyOtp("k", otp));
    }

    @Test
    void otpIsInvalidatedAfterTooManyWrongGuesses() {
        String otp = service.generateOtp("k");
        String wrong = otp.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < OtpService.MAX_FAILED_ATTEMPTS; i++) {
            assertFalse(service.verifyOtp("k", wrong));
        }
        assertFalse(service.verifyOtp("k", otp));
    }
}
