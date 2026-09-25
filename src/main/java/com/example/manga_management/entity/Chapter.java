package com.example.manga_management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chapter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @Id
    @Column(name = "ChapterID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "SeriesID", nullable = false)
    private Series series;

    @Column(name = "Chaptername", nullable = false, length = 30)
    private String chapterName;

    @Column(name = "Chapternumber")
    private Integer chapterNumber;

    @Column(name = "Deadline", columnDefinition = "DATETIME")
    private LocalDateTime deadline;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "tantou_comment", length = 500)
    private String tantouComment;

    @Column(name = "Script", length = 3000)
    private String script;

    /** Thời điểm Mangaka nộp chapter gần nhất để Tantou duyệt. */
    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    /** Thời điểm tantou duyệt hoặc từ chối chapter này (dùng cho lịch sử hoạt động). */
    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    /**
     * Đánh dấu chapter này đã được tính vào 1 lần thưởng series (10%/chap) rồi.
     * Một khi = true thì không được tính vào lần thưởng nào khác nữa, kể cả
     * khi series được vote thưởng lại ở kỳ sau.
     */
    @Column(name = "IsReward", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isReward = false;
}
