package com.example.manga_management.repository;

import com.example.manga_management.entity.EditorialVote;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EditorialVoteRepository extends JpaRepository<EditorialVote, String> {

    boolean existsByProposalIdAndBoardId(String proposalId, String boardId);

    List<EditorialVote> findByProposalId(String proposalId);

    long countByProposalId(String proposalId);

    @Query("SELECT COUNT(e) FROM EditorialVote e WHERE e.proposal.series.id = :seriesId")
    long countBySeriesId(@Param("seriesId") String seriesId);

    @Query("SELECT COUNT(e) FROM EditorialVote e " +
           "WHERE e.proposal.series.id = :seriesId AND e.vote = :voteType")
    long countBySeriesIdAndVote(@Param("seriesId") String seriesId,
                                 @Param("voteType") String voteType);

    @Query("SELECT COUNT(e) FROM EditorialVote e " +
           "WHERE e.proposal.series.id = :seriesId AND e.board.id = :boardId")
    long countBySeriesIdAndBoardId(@Param("seriesId") String seriesId,
                                    @Param("boardId") String boardId);

    List<EditorialVote> findByBoard_User_IdOrderByVoteDateDesc(String userId);
}
