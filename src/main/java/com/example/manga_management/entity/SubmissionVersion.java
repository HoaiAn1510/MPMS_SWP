package com.example.manga_management.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Một VÒNG giao việc của một {@link Submission}.
 *
 * <p>Trước đây mỗi lần mangaka "giao lại", hệ thống sửa đè thẳng lên Submission
 * (đổi deadline/comment, set filePath = null) nên bản trợ lý đã nộp ở vòng
 * trước biến mất hoàn toàn, không còn cách nào biết trợ lý đã gửi những gì và
 * tác giả đã phản hồi ra sao. Mỗi vòng giờ được chốt lại thành một bản ghi bất
 * biến ở đây, kèm ảnh riêng nên không bị lần lưu sau ghi đè.
 *
 * <p>Quy ước ảnh (thư mục static/Submission):
 * <ul>
 *   <li>{submissionId}_v{round}_assigned.png — bản tác giả giao của vòng đó</li>
 *   <li>{submissionId}_v{round}_submitted.png — bản trợ lý nộp của vòng đó</li>
 * </ul>
 */
@Entity
@Table(name = "submission_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"SubmissionID", "RoundNo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionVersion {

    /** Kết quả tác giả xử lý vòng này. */
    public static final String RESULT_PENDING = "pending";
    public static final String RESULT_APPROVED = "approved";
    public static final String RESULT_REASSIGNED = "reassigned";
    /** Vòng bị huỷ vì chapter quá hạn nộp — không phải lỗi trợ lý. */
    public static final String RESULT_CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmissionID", nullable = false)
    private Submission submission;

    /** Vòng giao việc thứ mấy của submission này, bắt đầu từ 1. */
    @Column(name = "RoundNo", nullable = false)
    private Integer roundNo;

    /** Ảnh bản tác giả giao của riêng vòng này (snapshot bất biến). */
    @Column(name = "AssignedFilePath", length = 80)
    private String assignedFilePath;

    /** Ảnh bản trợ lý nộp của riêng vòng này (chỉ set khi trợ lý bấm Nộp bài). */
    @Column(name = "SubmittedFilePath", length = 80)
    private String submittedFilePath;

    /** Yêu cầu của tác giả cho vòng này (comment lúc giao / giao lại). */
    @Column(name = "AssignComment", length = 1000)
    private String assignComment;

    @Column(name = "Deadline")
    private LocalDateTime deadline;

    @Column(name = "AssignedAt", nullable = false)
    private LocalDateTime assignedAt;

    /** Thời điểm trợ lý bấm Nộp bài ở vòng này (null = chưa nộp). */
    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    /** Thời điểm tác giả duyệt hoặc giao lại vòng này. */
    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    /** pending | approved | reassigned — xem hằng số RESULT_* ở trên. */
    @Column(name = "Result", nullable = false, length = 20)
    private String result = RESULT_PENDING;

    /** Nhận xét của tác giả khi giao lại vòng này (lý do chưa đạt). */
    @Column(name = "ReviewComment", length = 1000)
    private String reviewComment;
}
