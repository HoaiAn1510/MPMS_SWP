package com.example.manga_management.repository;

import com.example.manga_management.entity.BoardProposalComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BoardProposalCommentRepository extends JpaRepository<BoardProposalComment, Long> {
    List<BoardProposalComment> findByProposal_Id(String proposalId);
    boolean existsByProposal_IdAndBoard_Id(String proposalId, String boardId);
    Optional<BoardProposalComment> findByProposal_IdAndBoard_Id(String proposalId, String boardId);
    long countByProposal_IdAndAction(String proposalId, String action);
    long countByProposal_Id(String proposalId);
    List<BoardProposalComment> findByBoard_User_IdOrderByCreatedAtDesc(String userId);
}