package com.example.manga_management.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private record OtpEntry(String otp, LocalDateTime expiry) {}

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a 6-digit OTP for the given userId and stores it with a 5-minute expiry.
     *
     * @param userId the user's ID used as the key
     * @return the generated OTP string
     */
    public String generateOtp(String userId) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);
        otpStore.put(userId, new OtpEntry(otp, expiry));
        return otp;
    }

    /**
     * Verifies the OTP for the given userId.
     * Removes the entry from the store after a successful match.
     *
     * @param userId the user's ID
     * @param otp    the OTP provided by the user
     * @return true if OTP is correct and not expired, false otherwise
     */
    public boolean verifyOtp(String userId, String otp) {
        OtpEntry entry = otpStore.get(userId);
        if (entry == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(entry.expiry())) {
            otpStore.remove(userId);
            return false;
        }
        if (!entry.otp().equals(otp)) {
            return false;
        }
        otpStore.remove(userId);
        return true;
    }
}
