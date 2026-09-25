package com.example.manga_management.service;

import org.springframework.stereotype.Service;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.UserRepository;

@Service
public class UserService {
     private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(String username, String password) {
        return userRepository
                .findByUsernameAndPassword(username, password)
                .orElse(null);
    }
}
