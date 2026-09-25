package com.example.manga_management.repository;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<Assistant, String> {
    List<Assistant> findByMangakaId(String mangakaId);

    Optional<Assistant> findByUserId(String userId);

    Optional<Assistant> findTopByOrderByIdDesc();
}
