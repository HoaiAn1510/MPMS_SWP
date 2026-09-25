package com.example.manga_management.repository;

import com.example.manga_management.entity.Submission;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, String> {
    Optional<Submission> findTopByOrderByIdDesc();

    boolean existsByPageIdIdAndStatus(
            String pageId,
            String status);

    // 1 trang có thể có nhiều submission theo thời gian (nhiều vòng giao việc
    // khác nhau, cho nhiều assistant khác nhau) — luôn lấy bản mới nhất làm task
    // "hiện hành" của trang.
    //
    // Sắp xếp theo ID chứ KHÔNG theo CreatedAt: CreatedAt là @CreationTimestamp
    // ghi vào cột DATETIME (độ phân giải 1 giây), nên hai submission của cùng một
    // trang tạo trong cùng một giây sẽ cho thứ tự không xác định — làm sai cả chốt
    // chặn giao việc lẫn nút Duyệt. ID "SUBxxxx" cấp tăng dần nên so sánh chuỗi
    // của nó chính là thứ tự thời gian thật.
    Optional<Submission> findTopByPageIdIdOrderByIdDesc(String pageId);

    List<Submission> findByAssistant_IdOrderByDeadlineAsc(String assistantId);

    List<Submission> findByStatusAndDeadlineBetween(String status, LocalDateTime start, LocalDateTime end);

    List<Submission> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);

    List<Submission> findByAssistant_IdAndStatus(
            String assistantId,
            String status);

    List<Submission> findByAssistant_Id(String id);

    List<Submission> findByAssistant_Mangaka_IdOrderByCreatedAtDesc(String mangakaId);

    List<Submission> findByAssistant_Mangaka_IdAndStatus(String mangakaId, String status);

    List<Submission> findByPageIdId(String pageId);

    // Task chưa duyệt xong (intask/done) thuộc 1 series — dùng để huỷ hàng loạt khi series bị "stopped".
    List<Submission> findByPageId_Chapter_Series_IdAndStatusIn(String seriesId, List<String> statuses);
}
