package com.example.manga_management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.service.ActivityLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/chapter-review")
@Tag(name = "Chapter Review", description = "Giai đoạn D: Mangaka submit chapter → Tantou duyệt → Published / Reject")
public class ChapterReviewController {

    private final ChapterRepository chapterRepository;
    private final MangaPageRepository mangaPageRepository;
    private final NotificationController notificationController;
    private final ActivityLogService activityLogService;

    public ChapterReviewController(ChapterRepository chapterRepository,
            NotificationController notificationController,
            MangaPageRepository mangaPageRepository,
            ActivityLogService activityLogService) {
        this.chapterRepository = chapterRepository;
        this.notificationController = notificationController;
        this.mangaPageRepository = mangaPageRepository;
        this.activityLogService = activityLogService;
    }

    // ── Mangaka bấm "Submit Chapter" ──────────────────────────────────────
    @Operation(summary = "Mangaka submit chapter lên Tantou duyệt",
            description = "Đổi status chapter từ 'unfinish' hoặc 'reject_in_review' sang 'ready_to_review', gửi thông báo cho Tantou")
    @Transactional
    @PostMapping("/{chapterId}/submit")
    public Map<String, String> submitChapter(@PathVariable String chapterId,
            HttpSession session) {
        Map<String, String> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter!");
            return result;
        }

        if (chapter.getSeries() == null || chapter.getSeries().getProposal() == null
                || chapter.getSeries().getProposal().getMangaka() == null
                || chapter.getSeries().getProposal().getMangaka().getUser() == null
                || !chapter.getSeries().getProposal().getMangaka().getUser().getId().equals(user.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền submit chapter này!");
            return result;
        }

        if (!chapter.getStatus().equals("unfinish")) {
            result.put("status", "error");
            result.put("message", "Chapter này không thể submit lúc này (trạng thái: " + chapter.getStatus() + ")");
            return result;
        }

        if (chapter.getSeries() != null && chapter.getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", chapter.getSeries().getLockMessage());
            return result;
        }

// Kiểm tra chapter phải có trang
        List<MangaPage> pages = mangaPageRepository.findByChapterId(chapterId);
        if (pages.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Chapter chưa có trang nào, không thể submit!");
            return result;
        }

// Kiểm tra tất cả page phải là finish
        boolean allFinish = pages.stream().allMatch(p -> "finish".equals(p.getStatus()));
        if (!allFinish) {
            result.put("status", "error");
            result.put("message", "Tất cả trang phải hoàn thành (finish) trước khi submit!");
            return result;
        }

        chapter.setStatus("finish");
        chapter.setSubmittedAt(java.time.LocalDateTime.now());
        chapter.setReviewedAt(null);
        chapterRepository.save(chapter);

        String editorUserId = chapter.getSeries()
                .getProposal().getMangaka().getEditor().getUser().getId();
        String chapterName = chapter.getChapterName();
        String seriesName = chapter.getSeries().getSeriesName();

        notificationController.send(
                null,
                editorUserId,
                "Chapter '" + chapterName + "' của series '" + seriesName + "' đang chờ bạn duyệt.",
                "/manga/tantou"
        );
        activityLogService.log(user.getId(), "submit-chapter",
                "Đã nộp chapter \"" + chapterName + "\" (series \"" + seriesName + "\") lên biên tập viên");

        result.put("status", "success");
        result.put("message", "Đã submit chapter lên biên tập viên!");
        return result;
    }

    // ── Tantou lấy danh sách chapter cần duyệt ────────────────────────────
    @Operation(summary = "Tantou lấy danh sách chapter đang chờ duyệt",
            description = "Trả về các chapter có status 'ready_to_review' thuộc Mangaka của Tantou này")
    @Transactional
    @GetMapping("/pending")
    public Map<String, Object> getPendingChapters(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        List<Chapter> pending = chapterRepository
                .findByStatusAndSeries_Proposal_Mangaka_Editor_User_Id("finish", user.getId());
        result.put("status", "success");
        result.put("total", pending.size());
        result.put("chapters", pending);
        return result;
    }

    // ── Tantou duyệt hoặc reject chapter ──────────────────────────────────
    @Operation(summary = "Tantou duyệt hoặc từ chối chapter",
            description = "action=approve → status: published | action=reject → status: reject_in_review, gửi thông báo cho Mangaka")
    @Transactional
    @PostMapping("/{chapterId}/review")
    public Map<String, String> reviewChapter(@PathVariable String chapterId,
            @RequestParam String action,
            @RequestParam(required = false) String comment,
            HttpSession session) {
        Map<String, String> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter!");
            return result;
        }

        if (chapter.getSeries() == null || chapter.getSeries().getProposal() == null
                || chapter.getSeries().getProposal().getMangaka() == null
                || chapter.getSeries().getProposal().getMangaka().getEditor() == null
                || chapter.getSeries().getProposal().getMangaka().getEditor().getUser() == null
                || !chapter.getSeries().getProposal().getMangaka().getEditor().getUser().getId()
                        .equals(user.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không phải tantou phụ trách chapter này!");
            return result;
        }

        if (!chapter.getStatus().equals("finish")) {
            result.put("status", "error");
            result.put("message", "Chapter này không ở trạng thái chờ duyệt!");
            return result;
        }

        if (chapter.getSeries() != null && chapter.getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", chapter.getSeries().getLockMessage());
            return result;
        }

        String mangakaUserId = chapter.getSeries().getProposal().getMangaka().getUser().getId();
        String seriesName = chapter.getSeries().getSeriesName();
        String chapterName = chapter.getChapterName();

        if ("approve".equals(action)) {
            chapter.setStatus("pass");
            chapter.setReviewedAt(java.time.LocalDateTime.now());
            if (comment != null && !comment.isBlank()) {
                chapter.setTantouComment(comment.trim());
            }
            chapterRepository.save(chapter);

            String notifMsg = "✅ Chapter '" + chapterName + "' của series '" + seriesName + "' đã được duyệt!"
                    + (comment != null && !comment.isBlank() ? " Nhận xét: " + comment.trim() : "");
            notificationController.send(
                    null,
                    mangakaUserId,
                    notifMsg,
                    "/manga/mangaka"
            );

            result.put("status", "success");
            result.put("message", "Đã duyệt và xuất bản chapter!");

        } else if ("reject".equals(action)) {
            // Từ chối chapter BẮT BUỘC phải có nhận xét / yêu cầu chỉnh sửa để
            // mangaka biết cần sửa gì.
            if (comment == null || comment.isBlank()) {
                result.put("status", "error");
                result.put("message", "Vui lòng nhập nhận xét / yêu cầu chỉnh sửa khi từ chối chapter!");
                return result;
            }
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            chapter.setStatus("unfinish");
            chapter.setReviewedAt(now);
            chapter.setTantouComment(comment.trim());

            // Từ chối = yêu cầu sửa lại. Nếu deadline đã quá hạn thì phải gia hạn
            // (mặc định +7 ngày), nếu không job autoFinishOverdueChapters sẽ thấy
            // chapter "unfinish" đang quá hạn và TỰ ĐỘNG NỘP LẠI ngay → chapter
            // nhảy về "chờ duyệt" (vòng lặp). Chapter còn hạn thì giữ nguyên.
            if (chapter.getDeadline() == null || chapter.getDeadline().isBefore(now)) {
                chapter.setDeadline(now.plusDays(7).withHour(23).withMinute(59).withSecond(0).withNano(0));
            }

            // Reset status tất cả page về unfinish
            List<MangaPage> pages = mangaPageRepository.findByChapterId(chapterId);
            for (MangaPage p : pages) {
                p.setStatus("unfinish");
            }
            mangaPageRepository.saveAll(pages);

            chapterRepository.save(chapter);

            String notifMsg = "❌ Chapter '" + chapterName + "' của series '" + seriesName
                    + "' bị yêu cầu chỉnh sửa."
                    + (comment != null && !comment.isBlank() ? " Nhận xét: " + comment.trim() : "");

            notificationController.send(null, mangakaUserId, notifMsg, "/manga/mangaka");

            result.put("status", "success");
            result.put("message", "Đã yêu cầu chỉnh sửa chapter!");

        } else {
            result.put("status", "error");
            result.put("message", "Action không hợp lệ (approve hoặc reject)!");
        }

        return result;
    }
}
