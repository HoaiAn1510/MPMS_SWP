package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "editorialVote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EditorialVote {

    @Id
    @Column(name = "EvoteID", length = 6)
    private String evoteID;

    @ManyToOne
    @JoinColumn(name = "ProposalID", nullable = false)
    private Proposal proposal;

    @ManyToOne
    @JoinColumn(name = "BoardID", nullable = false)
    private Board board;

    @Column(name = "Vote", nullable = false)
    private String vote;

    @Column(name = "VoteDate", nullable = false)
    private LocalDate voteDate;
}
