package com.example.manga_management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;

public interface TantoEditorRepository extends JpaRepository<TantoEditor, String> {
    Optional<TantoEditor> findByUser(User user);

    Optional<TantoEditor> findByUserId(String userId);

    Optional<TantoEditor> findTopByOrderByIdDesc();
}
