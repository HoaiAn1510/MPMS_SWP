package com.example.manga_management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.service.ActivityLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/chapters")
@Tag(name = "Chapter", description = "Chapter Management APIs")
public class ChapterController {

    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private MangaPageRepository mangaPageRepository;
    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping("/{chapterId}/pages")
    @Operation(summary = "[SWAGGER] Lấy danh sách trang của một chapter")
    @ResponseBody
    public Map<String, Object> getPagesByChapter(@PathVariable String chapterId) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        List<MangaPage> pages = mangaPageRepository.findByChapterId(chapterId);
        result.put("status", "success");
        result.put("chapterId", chapterId);
        result.put("pages", pages);
        return result;
    }

    @PostMapping("/{chapterId}/script")
    @Operation(summary = "[SWAGGER] Lưu / sửa kịch bản của một chapter (tối đa 3000 chữ)")
    @ResponseBody
    public Map<String, Object> saveChapterScript(@PathVariable String chapterId,
            @RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        User requester = (User) session.getAttribute("user");
        Series chapterSeries = chapter.getSeries();
        boolean isOwner = requester != null && chapterSeries != null && chapterSeries.getProposal() != null
                && chapterSeries.getProposal().getMangaka() != null
                && chapterSeries.getProposal().getMangaka().getUser() != null
                && chapterSeries.getProposal().getMangaka().getUser().getId().equals(requester.getId());
        if (!isOwner) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền sửa kịch bản chapter này!");
            return result;
        }

        if (!"unfinish".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể sửa kịch bản khi chapter đang ở trạng thái unfinish");
            return result;
        }

        if (chapter.getSeries() != null && chapter.getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", chapter.getSeries().getLockMessage());
            return result;
        }

        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập kịch bản!");
            return result;
        }
        if (script.trim().length() > 3000) {
            result.put("status", "error");
            result.put("message", "Kịch bản tối đa 3000 chữ!");
            return result;
        }

        boolean wasEmpty = chapter.getScript() == null || chapter.getScript().trim().isEmpty();
        chapter.setScript(script.trim());
        chapterRepository.save(chapter);

        User user = (User) session.getAttribute("user");
        if (user != null) {
            activityLogService.log(user.getId(), wasEmpty ? "create-script" : "edit-script",
                    (wasEmpty ? "Đã tạo kịch bản cho chapter \"" : "Đã sửa kịch bản chapter \"")
                            + chapter.getChapterName() + "\"");
        }

        result.put("status", "success");
        result.put("message", "Đã lưu kịch bản!");
        result.put("chapterId", chapterId);
        return result;
    }

}
