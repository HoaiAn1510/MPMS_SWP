package com.example.manga_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.manga_management.entity.Series;

@Repository
public interface SeriesRepository extends JpaRepository<Series, String> {
    List<Series> findByProposal_Mangaka_Id(String mangakaId);

    List<Series> findByProposal_Mangaka_IdAndStatusIn(String mangakaId, List<String> statuses);

    List<Series> findByStatusAndProposal_Mangaka_Editor_Id(String status, String editorId);

    List<Series> findByStatus(String status);

    Optional<Series> findTopByOrderByIdDesc();

    // Kiểm tra trùng tên series (không phân biệt hoa thường).
    boolean existsBySeriesNameIgnoreCase(String seriesName);
}
