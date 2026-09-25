package com.example.manga_management.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.service.FileStorageService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionRepository submissionRepository;
    private final MangaPageRepository mangaPageRepository;
    private final FileStorageService fileStorageService;

    @PostMapping("/{submissionId}/savefile")
    @ResponseBody
    public Map<String, String> saveSubmissionFile(@PathVariable String submissionId,
            @RequestBody Map<String, String> body, HttpSession session) {
        Map<String, String> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy submission: " + submissionId);
                return result;
            }

            if (submission.getAssistant() == null || submission.getAssistant().getUser() == null
                    || !submission.getAssistant().getUser().getId().equals(user.getId())) {
                result.put("status", "error");
                result.put("message", "Bạn không phải trợ lý được giao bài nộp này!");
                return result;
            }

            // Chỉ được lưu khi task ĐANG LÀM. Trước đây endpoint này không kiểm tra
            // trạng thái, nên trợ lý vẫn ghi đè được ảnh sau khi đã bấm Nộp bài
            // (status "done", tác giả đang xem để duyệt) hoặc thậm chí sau khi tác
            // giả đã Duyệt (status "finish", đã tính lương) — thứ tác giả duyệt có
            // thể khác hẳn thứ tác giả đã xem.
            if (!"intask".equals(submission.getStatus())) {
                result.put("status", "error");
                result.put("message", "done".equals(submission.getStatus())
                        ? "Bài đã nộp và đang chờ tác giả duyệt, không thể sửa. Hãy đợi tác giả duyệt hoặc giao lại."
                        : "Task này đã kết thúc, không thể sửa bài nữa!");
                return result;
            }

            // Series bị dừng / chờ hồ sơ bảo vệ / đã hoàn thành thì khoá luôn thao
            // tác vẽ, đồng bộ với các endpoint khác trong luồng sản xuất.
            var chapter = submission.getPageId() != null ? submission.getPageId().getChapter() : null;
            if (chapter != null && chapter.getSeries() != null && chapter.getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", chapter.getSeries().getLockMessage());
                return result;
            }

            String base64 = body.get("imageBase64");
            if (base64 == null || base64.isBlank()) {
                result.put("status", "error");
                result.put("message", "Không có dữ liệu ảnh");
                return result;
            }
            if (base64.contains(",")) {
                base64 = base64.split(",")[1];
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64);
            String savedPath = fileStorageService.saveBytes(FileStorageService.DIR_SUBMISSION,
                    submissionId + ".png", imageBytes, FileStorageService.IMAGE_EXTENSIONS);

            submission.setFilePath(savedPath);
            submissionRepository.save(submission);

            // KHÔNG ghi đè ảnh TRANG CHÍNH THỨC (pageId.png) ở đây nữa. Bài trợ lý
            // chỉ nằm trong submission cho tới khi tác giả DUYỆT — lúc đó mới copy
            // sang trang chính thức (xem PageController.approvePageDone). Tránh việc
            // bản chưa duyệt đè lên bản tác giả đang giữ.

            result.put("status", "success");
            result.put("message", "Lưu bài nộp thành công!");
            result.put("redirectUrl", "/manga/assistant");
        } catch (IllegalArgumentException e) {
            result.put("status", "error");
            result.put("message", "Ảnh không hợp lệ (cần PNG/JPG/WEBP dạng base64)");
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi ghi file: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }
}
