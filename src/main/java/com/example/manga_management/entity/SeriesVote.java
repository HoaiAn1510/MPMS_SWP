package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "SeriesVote",
       uniqueConstraints = @UniqueConstraint(columnNames = { "SessionID", "BoardID" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeriesVote {

    @Id
    @Column(name = "SvoteID", length = 6)
    private String ID;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @ManyToOne
    @JoinColumn(name = "BoardID", nullable = false)
    private Board board;

    // Gắn đúng vào phiên vote đã cast, tránh đếm nhầm phiếu của phiên khác
    // cùng ngày (VoteSession.createdAt chỉ có độ chính xác theo ngày).
    @ManyToOne
    @JoinColumn(name = "SessionID")
    private VoteSession session;

    @Column(name = "Vote", nullable = false, length = 10)
    private String vote;

    @Column(name = "VoteDate", nullable = false)
    private LocalDate voteDate;

    @Column(name = "Content", length = 1000)
    private String content;
}