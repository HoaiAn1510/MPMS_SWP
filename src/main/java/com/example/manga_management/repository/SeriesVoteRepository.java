package com.example.manga_management.repository;

import com.example.manga_management.entity.SeriesVote;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesVoteRepository extends JpaRepository<SeriesVote, String> {
    long countBySeries_Id(String seriesId);

    long countBySeries_IdAndVote(String seriesId, String vote);

    long countBySeries_IdAndBoard_Id(String seriesId, String boardId);

    List<SeriesVote> findByBoard_User_IdOrderByVoteDateDesc(String userId);

    List<SeriesVote> findBySeries_IdOrderByVoteDateDesc(String seriesId);

    Optional<SeriesVote> findFirstBySession_IdAndBoard_Id(String sessionId, String boardId);

    long countBySession_Id(String sessionId);

    long countBySession_IdAndVote(String sessionId, String vote);
}
