package com.example.manga_management.repository;

import com.example.manga_management.entity.VoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VoteSessionRepository extends JpaRepository<VoteSession, String> {
    List<VoteSession> findByStatusOrderByCreatedAtDesc(String status);
    List<VoteSession> findByAutoCreatedOrderByCreatedAtDesc(boolean autoCreated);
    List<VoteSession> findByCreatedBy_User_IdOrderByCreatedAtDesc(String userId);
    boolean existsBySeriesIdAndStatus(String seriesId, String status);
    boolean existsBySeriesIdAndAutoCreatedAndStatus(String seriesId, boolean autoCreated, String status);
    boolean existsBySeriesIdAndVoteType(String seriesId, String voteType);
    Optional<VoteSession> findFirstBySeriesIdAndVoteTypeAndStatus(String seriesId, String voteType, String status);

    List<VoteSession> findBySeriesIdOrderByCreatedAtDesc(String seriesId);

    // Phiên "stop" đã pass gần nhất của 1 series — closedAt = mốc series bắt đầu "pending_cancel",
    // dùng để tính hạn 1 tuần chờ Tantou nộp hồ sơ bảo vệ.
    Optional<VoteSession> findFirstBySeriesIdAndVoteTypeAndStatusAndResultPassedTrueOrderByClosedAtDesc(
            String seriesId, String voteType, String status);

    // Tổng thưởng "reward" đã pass trong tháng, theo tất cả series của 1 mangaka — dùng tính "thưởng tháng này".
    List<VoteSession> findBySeries_Proposal_Mangaka_IdAndVoteTypeAndResultPassedTrueAndClosedAtBetween(
            String mangakaId, String voteType, LocalDate start, LocalDate end);
}
