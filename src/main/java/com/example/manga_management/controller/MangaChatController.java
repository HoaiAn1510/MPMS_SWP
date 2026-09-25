package com.example.manga_management.controller;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.FrameTask;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.FrameTaskRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.service.MangaChatService;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class MangaChatController {

    @Autowired
    private MangaChatService mangaChatService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private MangaPageRepository mangaPageRepository;

    @Autowired
    private FrameTaskRepository frameTaskRepository;

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "mask", required = false) MultipartFile mask,
            @RequestParam(value = "pageId", required = false) String pageId,
            @RequestParam(value = "submissionId", required = false) String submissionId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "pageType", required = false) String pageType,
            @RequestParam(value = "chapterScript", required = false) String chapterScript,
            @RequestParam(value = "pageScript", required = false) String pageScript,
            @RequestParam(value = "frameNotes", required = false) String frameNotes,
            @RequestParam(value = "selectedRegion", required = false) String selectedRegion,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        if (message == null) {
            message = "";
        }

        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            response.put("status", "error");
            response.put("type", "text");
            response.put("content", "Chưa đăng nhập!");
            response.put("message", "Chưa đăng nhập!");
            return ResponseEntity.status(401).body(response);
        }
        if (!canAccessContext(currentUser, pageId, submissionId)) {
            response.put("status", "error");
            response.put("type", "text");
            response.put("content", "Bạn không có quyền truy cập dữ liệu của trang/bài nộp này!");
            response.put("message", "Bạn không có quyền truy cập dữ liệu của trang/bài nộp này!");
            return ResponseEntity.status(403).body(response);
        }

        try {
            List<Map<String, Object>> history = getSessionHistory(session);
            Map<String, Object> context = buildContext(
                    pageId,
                    submissionId,
                    role,
                    pageType,
                    chapterScript,
                    pageScript,
                    frameNotes,
                    selectedRegion);
            boolean hasImage = image != null && !image.isEmpty();
            boolean hasMask = mask != null && !mask.isEmpty();
            if (hasMask) {
                context.put("selectionMask", Map.of("present", true, "mode", "pixel_mask"));
            }

            Map<String, Object> decision = mangaChatService.planChatAction(message, context, history, hasImage);
            String action = stringValue(decision.get("action"));
            String imagePrompt = stringValue(decision.get("imagePrompt"));
            String textResponse = stringValue(decision.get("textResponse"));

            response.put("status", "success");
            response.put("action", action);
            response.put("intent", decision.getOrDefault("intent", "general_help"));
            response.put("applyMode", decision.getOrDefault("applyMode", "no_apply"));
            response.put("warnings", decision.getOrDefault("warnings", List.of()));
            response.put("message", textResponse);

            if ("edit_image".equals(action)) {
                if (!hasImage) {
                    response.put("type", "text");
                    response.put("content", "MÃ¬nh cáº§n áº£nh hiá»‡n táº¡i Ä‘á»ƒ chá»‰nh. HÃ£y gá»­i canvas/áº£nh kÃ¨m yÃªu cáº§u nhÃ©.");
                    response.put("action", "ask_clarification");
                } else {
                    String base64Image = mangaChatService.editImage(image, mask, imagePrompt);
                    if (base64Image != null) {
                        response.put("type", "image");
                        response.put("content", base64Image);
                        response.put("imagePrompt", imagePrompt);
                    } else {
                        response.put("status", "error");
                        response.put("type", "text");
                        response.put("content", "Lá»—i: KhÃ´ng thá»ƒ chá»‰nh sá»­a áº£nh AI.");
                    }
                }
            } else if ("generate_image".equals(action)) {
                String base64Image = mangaChatService.generateImage(imagePrompt);
                if (base64Image != null) {
                    response.put("type", "image");
                    response.put("content", base64Image);
                    response.put("imagePrompt", imagePrompt);
                } else {
                    response.put("status", "error");
                    response.put("type", "text");
                    response.put("content", "Lá»—i: KhÃ´ng thá»ƒ táº¡o áº£nh AI.");
                }
            } else {
                response.put("type", "text");
                response.put("content", textResponse.isBlank()
                        ? mangaChatService.generateChatResponse(message, context, history)
                        : textResponse);
            }

            saveToHistory(session, "user", message, hasImage, null, null, null);
            saveToHistory(
                    session,
                    "ai",
                    stringValue(response.get("content")),
                    "image".equals(response.get("type")),
                    action,
                    stringValue(decision.get("intent")),
                    imagePrompt);

            return ResponseEntity.ok(response);

        } catch (MangaChatService.GeminiRateLimitException e) {
            int retryAfterSeconds = e.getRetryAfterSeconds();
            response.put("status", "rate_limited");
            response.put("type", "text");
            response.put("retryAfterSeconds", retryAfterSeconds);
            response.put("action", "chat_text");
            response.put("applyMode", "no_apply");
            response.put("warnings", List.of("Gemini quota dang bi gioi han tam thoi."));
            response.put("content", "Gemini dang qua tai/quota. Thu lai sau khoang " + retryAfterSeconds + " giay nhe.");
            response.put("message", response.get("content"));
            return ResponseEntity.status(429).body(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("type", "text");
            response.put("content", "Loi he thong: " + e.getMessage());
            response.put("message", "Loi he thong: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(HttpSession session) {
        return ResponseEntity.ok(getSessionHistory(session));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearHistory(HttpSession session) {
        session.removeAttribute("chatHistory");
        return ResponseEntity.ok(Map.of("status", "success", "message", "OK"));
    }

    private Map<String, Object> buildContext(
            String pageId,
            String submissionId,
            String role,
            String pageType,
            String chapterScript,
            String pageScript,
            String frameNotes,
            String selectedRegion) {
        Map<String, Object> context = new HashMap<>();
        putIfPresent(context, "pageId", pageId);
        putIfPresent(context, "submissionId", submissionId);
        putIfPresent(context, "role", role);
        putIfPresent(context, "pageType", pageType);
        putIfPresent(context, "chapterScript", chapterScript);
        putIfPresent(context, "pageScript", pageScript);
        putIfPresent(context, "frameNotes", frameNotes);
        putIfPresent(context, "selectedRegion", selectedRegion);

        Optional<Submission> submissionOpt = findSubmission(submissionId, pageId);
        MangaPage page = submissionOpt.map(Submission::getPageId)
                .orElseGet(() -> findPage(pageId).orElse(null));

        submissionOpt.ifPresent(submission -> addTaskContext(context, submission));
        if (page != null) {
            addPageContext(context, page);
            addStoryContext(context, page);
            addStyleBible(context, page);
            addFeedbackContext(context, submissionOpt.orElse(null), page);
        }
        submissionOpt.ifPresent(submission -> addFrameContext(context, submission.getId()));

        return context;
    }

    /**
     * Chỉ mangaka chủ sở hữu series, trợ lý đang được giao task, hoặc tantou
     * phụ trách mangaka đó mới được lấy context nội bộ của 1 page/submission.
     * Không truyền pageId/submissionId (chat chung, không gắn ngữ cảnh) luôn
     * được phép.
     */
    private boolean canAccessContext(User user, String pageId, String submissionId) {
        if ((pageId == null || pageId.isBlank()) && (submissionId == null || submissionId.isBlank())) {
            return true;
        }
        if ("admin".equalsIgnoreCase(user.getRole())) {
            return true;
        }

        Optional<Submission> submissionOpt = findSubmission(submissionId, pageId);
        MangaPage page = submissionOpt.map(Submission::getPageId).orElseGet(() -> findPage(pageId).orElse(null));
        if (page == null) {
            // Không tìm thấy tài nguyên — để luồng xử lý chính báo lỗi "không tìm thấy".
            return true;
        }

        if (submissionOpt.isPresent()) {
            Submission submission = submissionOpt.get();
            if (submission.getAssistant() != null && submission.getAssistant().getUser() != null
                    && submission.getAssistant().getUser().getId().equals(user.getId())) {
                return true;
            }
        }

        Chapter chapter = page.getChapter();
        Series series = chapter != null ? chapter.getSeries() : null;
        Proposal proposal = series != null ? series.getProposal() : null;
        if (proposal == null || proposal.getMangaka() == null) {
            return false;
        }
        if (proposal.getMangaka().getUser() != null
                && proposal.getMangaka().getUser().getId().equals(user.getId())) {
            return true;
        }
        return proposal.getMangaka().getEditor() != null
                && proposal.getMangaka().getEditor().getUser() != null
                && proposal.getMangaka().getEditor().getUser().getId().equals(user.getId());
    }

    private Optional<Submission> findSubmission(String submissionId, String pageId) {
        if (submissionId != null && !submissionId.isBlank()) {
            Optional<Submission> byId = submissionRepository.findById(submissionId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (pageId != null && !pageId.isBlank()) {
            return submissionRepository.findTopByPageIdIdOrderByIdDesc(pageId);
        }
        return Optional.empty();
    }

    private Optional<MangaPage> findPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return Optional.empty();
        }
        return mangaPageRepository.findById(pageId);
    }

    private void addTaskContext(Map<String, Object> context, Submission submission) {
        Map<String, Object> task = new HashMap<>();
        task.put("submissionId", submission.getId());
        task.put("status", submission.getStatus());
        task.put("deadline", stringValue(submission.getDeadline()));
        task.put("deadlineState", deadlineState(submission.getDeadline()));
        putIfPresent(task, "mangakaInstruction", submission.getComment());
        context.put("task", task);
    }

    private void addPageContext(Map<String, Object> context, MangaPage page) {
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("pageId", page.getId());
        pageInfo.put("pageNumber", page.getPageNumber());
        pageInfo.put("status", page.getStatus());
        pageInfo.put("pageType", page.getPageType());
        putIfPresent(pageInfo, "script", page.getScript());
        putIfPresent(pageInfo, "tantouComment", page.getTantouComment());
        context.put("page", pageInfo);
    }

    private void addStoryContext(Map<String, Object> context, MangaPage page) {
        Chapter chapter = page.getChapter();
        if (chapter == null) {
            return;
        }
        Map<String, Object> story = new HashMap<>();
        story.put("chapterId", chapter.getId());
        story.put("chapterName", chapter.getChapterName());
        story.put("chapterNumber", chapter.getChapterNumber());
        story.put("chapterStatus", chapter.getStatus());
        story.put("chapterDeadline", stringValue(chapter.getDeadline()));
        putIfPresent(story, "chapterScript", chapter.getScript());
        putIfPresent(story, "chapterTantouComment", chapter.getTantouComment());
        context.put("story", story);
    }

    private void addStyleBible(Map<String, Object> context, MangaPage page) {
        Chapter chapter = page.getChapter();
        Series series = chapter != null ? chapter.getSeries() : null;
        Proposal proposal = series != null ? series.getProposal() : null;
        Map<String, Object> style = new HashMap<>();
        if (series != null) {
            style.put("seriesId", series.getId());
            style.put("seriesName", series.getSeriesName());
            style.put("genre", series.getGenre());
            style.put("description", series.getDescription());
            style.put("status", series.getStatus());
        }
        if (proposal != null) {
            putIfPresent(style, "proposalGenre", proposal.getGenre());
            putIfPresent(style, "proposalComment", proposal.getComment());
            if (proposal.getEditorScore() != null) {
                style.put("editorScore", proposal.getEditorScore());
            }
        }
        if (!style.isEmpty()) {
            style.put("rule", "Preserve this series tone, genre, recurring design language, and character consistency.");
            context.put("styleBible", style);
        }
    }

    private void addFeedbackContext(Map<String, Object> context, Submission submission, MangaPage page) {
        List<String> feedback = new ArrayList<>();
        if (submission != null) {
            addFeedback(feedback, "Mangaka task note", submission.getComment());
        }
        addFeedback(feedback, "Page Tantou comment", page.getTantouComment());
        Chapter chapter = page.getChapter();
        if (chapter != null) {
            addFeedback(feedback, "Chapter Tantou comment", chapter.getTantouComment());
            Series series = chapter.getSeries();
            Proposal proposal = series != null ? series.getProposal() : null;
            if (proposal != null) {
                addFeedback(feedback, "Proposal review comment", proposal.getComment());
            }
        }
        if (!feedback.isEmpty()) {
            context.put("tantouFeedback", feedback);
            context.put("revisionChecklist", buildRevisionChecklist(feedback));
        }
    }

    private void addFrameContext(Map<String, Object> context, String submissionId) {
        List<Map<String, Object>> frames = new ArrayList<>();
        for (FrameTask frame : frameTaskRepository.findBySubmission_IdOrderByFrameNumberAsc(submissionId)) {
            Map<String, Object> item = new HashMap<>();
            item.put("frameNumber", frame.getFrameNumber());
            putIfPresent(item, "content", frame.getContent());
            frames.add(item);
        }
        if (!frames.isEmpty()) {
            context.put("frames", frames);
        }
    }

    private void addFeedback(List<String> feedback, String label, String value) {
        if (value != null && !value.isBlank()) {
            feedback.add(label + ": " + value.trim());
        }
    }

    private List<String> buildRevisionChecklist(List<String> feedback) {
        List<String> checklist = new ArrayList<>();
        for (String item : feedback) {
            checklist.add("Address: " + item);
        }
        return checklist;
    }

    private String deadlineState(LocalDateTime deadline) {
        if (deadline == null) {
            return "not_set";
        }
        LocalDateTime now = LocalDateTime.now();
        if (deadline.isBefore(now)) {
            return "overdue";
        }
        long hours = Duration.between(now, deadline).toHours();
        if (hours <= 24) {
            return "due_within_24h";
        }
        if (hours <= 72) {
            return "due_soon";
        }
        return "scheduled";
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getSessionHistory(HttpSession session) {
        Object raw = session.getAttribute("chatHistory");
        if (raw instanceof List<?>) {
            return (List<Map<String, Object>>) raw;
        }
        return new ArrayList<>();
    }

    private void saveToHistory(
            HttpSession session,
            String role,
            String content,
            boolean isImage,
            String action,
            String intent,
            String imagePrompt) {
        List<Map<String, Object>> history = getSessionHistory(session);
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        msg.put("isImage", isImage);
        if (action != null && !action.isBlank()) {
            msg.put("action", action);
        }
        if (intent != null && !intent.isBlank()) {
            msg.put("intent", intent);
        }
        if (imagePrompt != null && !imagePrompt.isBlank()) {
            msg.put("imagePrompt", imagePrompt);
        }
        history.add(msg);
        session.setAttribute("chatHistory", history);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
