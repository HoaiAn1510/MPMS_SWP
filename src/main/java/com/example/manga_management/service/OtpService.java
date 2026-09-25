package com.example.manga_management.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    /** Số lần nhập sai tối đa trước khi OTP bị vô hiệu (chống dò mã 6 số). */
    static final int MAX_FAILED_ATTEMPTS = 5;

    private static final class OtpEntry {
        final String otp;
        final LocalDateTime expiry;
        int failedAttempts;

        OtpEntry(String otp, LocalDateTime expiry) {
            this.otp = otp;
            this.expiry = expiry;
        }
    }

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a 6-digit OTP for the given key and stores it with a 5-minute expiry.
     * Requesting a new OTP replaces the old one and resets the failed-attempt counter.
     *
     * @param userId the key the OTP is stored under
     * @return the generated OTP string
     */
    public String generateOtp(String userId) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);
        otpStore.put(userId, new OtpEntry(otp, expiry));
        return otp;
    }

    /**
     * Verifies the OTP for the given key.
     * The entry is removed after a successful match, after expiry, or after
     * {@link #MAX_FAILED_ATTEMPTS} wrong guesses.
     *
     * @return true if OTP is correct and not expired, false otherwise
     */
    public boolean verifyOtp(String userId, String otp) {
        OtpEntry entry = otpStore.get(userId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (LocalDateTime.now().isAfter(entry.expiry)) {
                otpStore.remove(userId, entry);
                return false;
            }
            if (otp == null || !java.security.MessageDigest.isEqual(
                    entry.otp.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    otp.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                if (++entry.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    otpStore.remove(userId, entry);
                }
                return false;
            }
            otpStore.remove(userId, entry);
            return true;
        }
    }
}
