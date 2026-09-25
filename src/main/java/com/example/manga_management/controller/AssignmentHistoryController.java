package com.example.manga_management.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.SubmissionVersion;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.SubmissionVersionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Timeline giao việc: mỗi vòng tác giả giao ra và mỗi bản trợ lý gửi lại đều
 * hiện thành một mốc, kèm ảnh riêng của vòng đó.
 *
 * <p>Trước đây dữ liệu này không tồn tại: "giao lại" sửa đè thẳng lên Submission
 * nên chỉ còn deadline/comment của vòng mới nhất, không có cách nào dựng lại
 * tác giả đã giao gì cho trợ lý nào vào lúc nào.
 */
@RestController
@RequestMapping("/api/assignment-history")
@RequiredArgsConstructor
@Tag(name = "AssignmentHistory", description = "Lịch sử giao việc và các phiên bản trợ lý đã gửi tác giả duyệt")
public class AssignmentHistoryController {

    private final SubmissionVersionRepository submissionVersionRepository;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;

    /** Một mốc timeline, phẳng để FE render thẳng không cần join thêm. */
    private Map<String, Object> toTimelineEntry(SubmissionVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        Submission s = v.getSubmission();
        MangaPage page = s != null ? s.getPageId() : null;

        m.put("versionId", v.getId());
        m.put("submissionId", s != null ? s.getId() : null);
        m.put("roundNo", v.getRoundNo());
        m.put("result", v.getResult());
        m.put("assignComment", v.getAssignComment());
        m.put("reviewComment", v.getReviewComment());
        m.put("deadline", v.getDeadline());
        m.put("assignedAt", v.getAssignedAt());
        m.put("submittedAt", v.getSubmittedAt());
        m.put("reviewedAt", v.getReviewedAt());
        // Hai ảnh của RIÊNG vòng này — không bị vòng sau ghi đè.
        m.put("assignedFilePath", v.getAssignedFilePath());
        m.put("submittedFilePath", v.getSubmittedFilePath());

        // Trễ hạn = có nộp nhưng nộp sau deadline, hoặc chưa nộp mà deadline đã qua.
        boolean late = v.getDeadline() != null
                && (v.getSubmittedAt() != null
                        ? v.getSubmittedAt().isAfter(v.getDeadline())
                        : java.time.LocalDateTime.now().isAfter(v.getDeadline())
                                && SubmissionVersion.RESULT_PENDING.equals(v.getResult()));
        m.put("late", late);

        if (s != null && s.getAssistant() != null) {
            m.put("assistantId", s.getAssistant().getId());
            m.put("assistantName", s.getAssistant().getUser() != null
                    ? s.getAssistant().getUser().getFullname() : null);
        }
        if (page != null) {
            m.put("pageId", page.getId());
            m.put("pageNumber", page.getPageNumber());
            if (page.getChapter() != null) {
                m.put("chapterId", page.getChapter().getId());
                m.put("chapterNumber", page.getChapter().getChapterNumber());
                m.put("chapterName", page.getChapter().getChapterName());
                if (page.getChapter().getSeries() != null) {
                    m.put("seriesId", page.getChapter().getSeries().getId());
                    m.put("seriesName", page.getChapter().getSeries().getSeriesName());
                }
            }
        }
        return m;
    }

    private List<Map<String, Object>> toTimeline(List<SubmissionVersion> versions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SubmissionVersion v : versions) {
            out.add(toTimelineEntry(v));
        }
        return out;
    }

    @Operation(summary = "Toàn bộ phiên bản của một trang — tác giả đối chiếu các vòng đã giao/đã nhận")
    @GetMapping("/page/{pageId}")
    public Map<String, Object> pageHistory(@PathVariable String pageId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        List<SubmissionVersion> versions =
                submissionVersionRepository.findBySubmission_PageId_IdOrderByAssignedAtDesc(pageId);

        // Chỉ người trong cuộc được xem: tác giả sở hữu trang, hoặc trợ lý đã từng
        // được giao chính trang đó.
        boolean allowed = versions.isEmpty() || versions.stream().anyMatch(v -> {
            Submission s = v.getSubmission();
            if (s == null) {
                return false;
            }
            boolean isAssistant = s.getAssistant() != null && s.getAssistant().getUser() != null
                    && s.getAssistant().getUser().getId().equals(user.getId());
            boolean isOwner = s.getPageId() != null && s.getPageId().getChapter() != null
                    && s.getPageId().getChapter().getSeries() != null
                    && s.getPageId().getChapter().getSeries().getProposal() != null
                    && s.getPageId().getChapter().getSeries().getProposal().getMangaka() != null
                    && s.getPageId().getChapter().getSeries().getProposal().getMangaka().getUser() != null
                    && s.getPageId().getChapter().getSeries().getProposal().getMangaka().getUser().getId()
                            .equals(user.getId());
            return isAssistant || isOwner;
        });
        if (!allowed) {
            return Map.of("status", "error", "message", "Bạn không có quyền xem lịch sử của trang này!");
        }

        return Map.of("status", "success", "pageId", pageId, "versions", toTimeline(versions));
    }

    @Operation(summary = "Các vòng của MỘT task — trợ lý xem lại mình đã gửi bản nào và bị trả về vì sao")
    @GetMapping("/submission/{submissionId}")
    public Map<String, Object> submissionHistory(@PathVariable String submissionId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        List<SubmissionVersion> versions =
                submissionVersionRepository.findBySubmission_IdOrderByRoundNoAsc(submissionId);
        if (versions.isEmpty()) {
            return Map.of("status", "success", "submissionId", submissionId,
                    "versions", List.of());
        }

        // Chỉ trợ lý được giao task này hoặc tác giả sở hữu series mới được xem.
        Submission s = versions.get(0).getSubmission();
        boolean isAssistant = s.getAssistant() != null && s.getAssistant().getUser() != null
                && s.getAssistant().getUser().getId().equals(user.getId());
        boolean isOwner = s.getPageId() != null && s.getPageId().getChapter() != null
                && s.getPageId().getChapter().getSeries() != null
                && s.getPageId().getChapter().getSeries().getProposal() != null
                && s.getPageId().getChapter().getSeries().getProposal().getMangaka() != null
                && s.getPageId().getChapter().getSeries().getProposal().getMangaka().getUser() != null
                && s.getPageId().getChapter().getSeries().getProposal().getMangaka().getUser().getId()
                        .equals(user.getId());
        if (!isAssistant && !isOwner) {
            return Map.of("status", "error", "message", "Bạn không có quyền xem lịch sử của bài nộp này!");
        }

        return Map.of(
                "status", "success",
                "submissionId", submissionId,
                "summary", summarize(versions),
                "versions", toTimeline(versions));
    }

    @Operation(summary = "Lịch sử tác giả đang đăng nhập đã giao việc cho một trợ lý cụ thể")
    @GetMapping("/assistant/{assistantId}")
    public Map<String, Object> assistantHistory(@PathVariable String assistantId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        Assistant assistant = assistantRepository.findById(assistantId).orElse(null);
        if (assistant == null) {
            return Map.of("status", "error", "message", "Không tìm thấy trợ lý: " + assistantId);
        }

        // Cho phép: chính trợ lý đó, hoặc mangaka đang quản lý trợ lý đó.
        boolean isSelf = assistant.getUser() != null && assistant.getUser().getId().equals(user.getId());
        boolean isManager = assistant.getMangaka() != null && assistant.getMangaka().getUser() != null
                && assistant.getMangaka().getUser().getId().equals(user.getId());
        if (!isSelf && !isManager) {
            return Map.of("status", "error", "message", "Bạn không có quyền xem lịch sử của trợ lý này!");
        }

        List<SubmissionVersion> versions =
                submissionVersionRepository.findBySubmission_Assistant_IdOrderByAssignedAtDesc(assistantId);
        List<Map<String, Object>> timeline = toTimeline(versions);

        return Map.of(
                "status", "success",
                "assistantId", assistantId,
                "assistantName", assistant.getUser() != null ? assistant.getUser().getFullname() : "",
                "summary", summarize(versions),
                "timeline", timeline);
    }

    @Operation(summary = "Toàn bộ lịch sử giao việc của tác giả đang đăng nhập, gộp mọi trợ lý")
    @GetMapping("/my-assignments")
    public Map<String, Object> myAssignments(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("status", "error", "message", "Vui lòng đăng nhập lại");
        }

        Mangaka mangaka = mangakaRepository.findByUserId(user.getId()).orElse(null);
        if (mangaka == null) {
            return Map.of("status", "error", "message", "Không tìm thấy Mangaka");
        }

        List<SubmissionVersion> versions = submissionVersionRepository
                .findBySubmission_Assistant_Mangaka_IdOrderByAssignedAtDesc(mangaka.getId());

        return Map.of(
                "status", "success",
                "summary", summarize(versions),
                "timeline", toTimeline(versions));
    }

    /** Số liệu tổng hợp hiển thị trên đầu timeline. */
    private Map<String, Object> summarize(List<SubmissionVersion> versions) {
        long approved = versions.stream()
                .filter(v -> SubmissionVersion.RESULT_APPROVED.equals(v.getResult())).count();
        long reassigned = versions.stream()
                .filter(v -> SubmissionVersion.RESULT_REASSIGNED.equals(v.getResult())).count();
        long pending = versions.stream()
                .filter(v -> SubmissionVersion.RESULT_PENDING.equals(v.getResult())).count();
        long late = versions.stream()
                .filter(v -> v.getDeadline() != null && v.getSubmittedAt() != null
                        && v.getSubmittedAt().isAfter(v.getDeadline()))
                .count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRounds", versions.size());
        m.put("approved", approved);
        m.put("reassigned", reassigned);
        m.put("pending", pending);
        m.put("lateSubmissions", late);
        return m;
    }
}
