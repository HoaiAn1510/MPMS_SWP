package com.example.manga_management.repository;

import com.example.manga_management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsernameAndPassword(String username, String password);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findTopByOrderByIdDesc();
}