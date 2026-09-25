package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "board_proposal_comment",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ProposalID", "BoardID"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardProposalComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProposalID", nullable = false)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BoardID", nullable = false)
    private Board board;

    // "pass" | "reject" — board duyệt hay không duyệt
    @Column(name = "Action", nullable = false, length = 10)
    private String action;

    @Column(name = "Content", length = 1000)
    private String content;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}