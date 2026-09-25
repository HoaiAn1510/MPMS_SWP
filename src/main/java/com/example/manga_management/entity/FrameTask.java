package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "frameTask")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FrameTask {

    @Id
    @Column(name = "FrameTaskID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "PageID", nullable = false)
    private MangaPage page;

    @ManyToOne
    @JoinColumn(name = "TaskID", nullable = false)
    private Submission submission;

    @Column(name = "FrameID", nullable = false)
    private Integer frameNumber;

    @Column(name = "content", length = 1000)
    private String content;
}
