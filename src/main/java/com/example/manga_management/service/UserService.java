package com.example.manga_management.service;

import org.springframework.stereotype.Service;

import com.example.manga_management.config.PasswordHasher;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !PasswordHasher.matches(password, user.getPassword())) {
            return null;
        }
        // Nâng cấp mật khẩu plaintext cũ lên BCrypt ngay lần đăng nhập thành công.
        if (PasswordHasher.needsUpgrade(user.getPassword())) {
            user.setPassword(PasswordHasher.hash(password));
            user = userRepository.save(user);
        }
        return user;
    }
}
