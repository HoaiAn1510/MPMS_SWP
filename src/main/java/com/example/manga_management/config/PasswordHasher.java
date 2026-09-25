package com.example.manga_management.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Băm và kiểm tra mật khẩu bằng BCrypt.
 *
 * Dữ liệu cũ có thể còn mật khẩu dạng plaintext; {@link #matches} vẫn chấp nhận
 * chúng để đăng nhập được, và {@link #needsUpgrade} cho biết cần băm lại.
 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordHasher() {
    }

    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.length() == 60
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        if (isHashed(stored)) {
            return ENCODER.matches(rawPassword, stored);
        }
        // Legacy plaintext: so sánh thời gian hằng số.
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean needsUpgrade(String stored) {
        return !isHashed(stored);
    }
}
