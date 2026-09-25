package com.example.manga_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.manga_management.entity.SubmissionVersion;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {

    List<SubmissionVersion> findBySubmission_IdOrderByRoundNoAsc(String submissionId);

    /** Vòng đang mở của một submission (vòng có số thứ tự lớn nhất). */
    Optional<SubmissionVersion> findTopBySubmission_IdOrderByRoundNoDesc(String submissionId);

    /** Toàn bộ vòng giao việc của một trang, mới nhất trước — dùng cho timeline trang. */
    List<SubmissionVersion> findBySubmission_PageId_IdOrderByAssignedAtDesc(String pageId);

    /** Toàn bộ vòng giao việc của một trợ lý — dùng cho timeline theo trợ lý. */
    List<SubmissionVersion> findBySubmission_Assistant_IdOrderByAssignedAtDesc(String assistantId);

    /** Toàn bộ vòng giao việc do một mangaka giao ra — dùng cho timeline của tác giả. */
    List<SubmissionVersion> findBySubmission_Assistant_Mangaka_IdOrderByAssignedAtDesc(String mangakaId);
}
