package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "likeResult")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LikeResult {

    @Id
    @Column(name = "VoteID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @Column(name = "likeNumber", nullable = false)
    private Integer likeNumber;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer dislikeNumber = 0;

    @Column(name = "viewCount", nullable = false, columnDefinition = "integer default 0")
    private Integer viewCount = 0;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;
}
