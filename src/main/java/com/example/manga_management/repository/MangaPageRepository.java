package com.example.manga_management.repository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MangaPageRepository extends JpaRepository<MangaPage, String> {
    List<MangaPage> findByChapter(Chapter chapter);
    Optional<MangaPage> findTopByOrderByIdDesc();
    
    List<MangaPage> findByChapterId(String chapterId);
        // Lấy tất cả page của chapter
    List<MangaPage> findByChapterIdOrderByPageNumberAsc(String chapterId);

    Optional<MangaPage> findFirstByChapterAndPageTypeOrderByPageNumberAsc(Chapter chapter, String pageType);

    // Lấy page có ID lớn nhất để sinh MGP001, MGP002,...


    // Lấy page cuối cùng của chapter để tính pageNumber tiếp theo
    MangaPage findTopByChapterIdOrderByPageNumberDesc(String chapterId);
}
