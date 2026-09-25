package com.example.manga_management.repository;

import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MangakaRepository extends JpaRepository<Mangaka, String> {
    Optional<Mangaka> findByUser(User user);

    Optional<Mangaka> findByUserId(String userId);

    List<Mangaka> findByEditor_Id(String editorId);

    Optional<Mangaka> findTopByOrderByIdDesc();
}