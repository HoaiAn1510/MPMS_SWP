package com.example.manga_management.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Series;

public interface ChapterRepository extends JpaRepository<Chapter, String> {

    List<Chapter> findBySeries(Series series);

    List<Chapter> findByStatus(String status);

    List<Chapter> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);

    List<Chapter> findBySeriesId(String seriesId);

    Optional<Chapter> findTopByOrderByIdDesc();

    Optional<Chapter> findTopBySeriesOrderByChapterNumberDesc(Series series);

    // Thêm 2 dòng này vào ChapterRepository (interface đã có sẵn), không sửa gì khác
    List<Chapter> findByStatusAndSeries_Proposal_Mangaka_Editor_User_Id(String status, String editorId);

    List<Chapter> findBySeries_IdAndStatus(String seriesId, String status);

    // Lịch sử hoạt động của tantou: các chapter họ đã duyệt/từ chối
    List<Chapter> findBySeries_Proposal_Mangaka_Editor_User_IdAndReviewedAtIsNotNullOrderByReviewedAtDesc(
            String editorUserId);

    // findBySeries_IdAndStatus(seriesId, "published") (đã có ở trên) dùng khi tính thưởng: lọc thêm !isReward() ở tầng service

    // Mọi chapter mangaka đã được tantou xử lý (duyệt/từ chối) trong khoảng thời gian —
    // dùng để tính "số chapter hoàn thành trong tháng" (lọc thêm status pass/published ở tầng service)
    List<Chapter> findBySeries_Proposal_Mangaka_IdAndReviewedAtBetween(
            String mangakaId, LocalDateTime start, LocalDateTime end);
}
