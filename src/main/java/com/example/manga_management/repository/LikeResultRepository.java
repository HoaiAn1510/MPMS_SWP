package com.example.manga_management.repository;

import com.example.manga_management.entity.LikeResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeResultRepository extends JpaRepository<LikeResult, String> {
    Optional<LikeResult> findBySeriesIdAndMonthAndYear(String seriesId, Integer month, Integer year);

    List<LikeResult> findBySeries_Id(String seriesId);
}
