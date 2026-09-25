package com.example.manga_management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "submission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @Column(name = "SubmissionID", length = 7)
    private String id;

    @ManyToOne
    @JoinColumn(name = "PageID", nullable = false)
    private MangaPage pageId;

    @ManyToOne
    @JoinColumn(name = "AssistantID", nullable = false)
    private Assistant assistant;

    @Column(name = "Deadline", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime deadline;

    /** Ảnh "bản trợ lý nộp" — bản vẽ trợ lý lưu/nộp (bị cập nhật mỗi lần trợ lý lưu). */
    @Column(name = "FilePath", length = 60)
    private String filePath;

    /**
     * Ảnh "bản tác giả giao" — snapshot BẤT BIẾN của trang tại thời điểm mangaka
     * giao việc. Lưu ở file riêng ({id}_assigned.png) nên trợ lý lưu bài về sau
     * KHÔNG ghi đè được, giúp mangaka so sánh bản giao ↔ bản trợ lý nộp.
     */
    @Column(name = "AssignedFilePath", length = 60)
    private String assignedFilePath;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ApprovedAt")
    private LocalDateTime approvedAt;

    /** Thời điểm trợ lý nộp bài (kể cả tự động nộp do quá hạn). */
    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    /**
     * Vòng giao việc hiện hành (1 = giao lần đầu, tăng thêm mỗi lần mangaka giao
     * lại). Ba trường filePath/assignedFilePath/deadline ở trên chỉ phản ánh vòng
     * này; lịch sử đầy đủ nằm ở {@link SubmissionVersion}.
     */
    @Column(name = "CurrentRound", nullable = false)
    private Integer currentRound = 1;
}
