package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.VoteSessionRepository;
import com.example.manga_management.service.EditorialAiService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/tantou")
public class TantouController {

    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final NotificationController notificationController;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final com.example.manga_management.repository.LikeResultRepository likeResultRepository;
    private final EditorialAiService editorialAiService;

    public TantouController(ProposalRepository proposalRepository, TantoEditorRepository tantoEditorRepository,
            NotificationController notificationController, MangakaRepository mangakaRepository,
            SeriesRepository seriesRepository, ChapterRepository chapterRepository,
            VoteSessionRepository voteSessionRepository,
            com.example.manga_management.repository.LikeResultRepository likeResultRepository,
            EditorialAiService editorialAiService) {
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.notificationController = notificationController;
        this.mangakaRepository = mangakaRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
        this.likeResultRepository = likeResultRepository;
        this.editorialAiService = editorialAiService;
    }

    @GetMapping("")
    public String tantouPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null)
            return "redirect:/login";

        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("tantouId", editor.getId());
        return "tantou";
    }

    @PostMapping("/ai/review-assist")
    @ResponseBody
    public Map<String, Object> aiReviewAssist(
            @RequestParam String proposalId,
            @RequestParam(required = false) String draft,
            HttpSession session) {
        return runEditorialAi("review_assist", proposalId, draft, session);
    }

    @PostMapping("/ai/feedback-polish")
    @ResponseBody
    public Map<String, Object> aiFeedbackPolish(
            @RequestParam String proposalId,
            @RequestParam(required = false) String draft,
            HttpSession session) {
        return runEditorialAi("feedback_polish", proposalId, draft, session);
    }

    private Map<String, Object> runEditorialAi(
            String mode, String proposalId, String draft, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Ch\u01b0a \u0111\u0103ng nh\u1eadp!");
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (editor == null || proposal == null || proposal.getMangaka() == null
                || proposal.getMangaka().getEditor() == null
                || !editor.getId().equals(proposal.getMangaka().getEditor().getId())) {
            result.put("status", "error");
            result.put("message", "B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n ph\u00e2n t\u00edch \u0111\u1ec1 xu\u1ea5t n\u00e0y.");
            return result;
        }

        try {
            return editorialAiService.assist("tantou", mode, proposalId, draft);
        } catch (EditorialAiService.EditorialAiRateLimitException e) {
            result.put("status", "rate_limited");
            result.put("retryAfterSeconds", e.getRetryAfterSeconds());
            result.put("message", "Gemini \u0111ang gi\u1edbi h\u1ea1n quota. H\u00e3y th\u1eed l\u1ea1i sau "
                    + e.getRetryAfterSeconds() + " gi\u00e2y.");
            return result;
        } catch (EditorialAiService.EditorialAiException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return result;
        }
    }

    @Operation(summary = "Danh sách bản thảo mới chờ duyệt")
    @GetMapping("/proposals")
    @ResponseBody
    public Map<String, Object> getProposals(@RequestParam String tantouId) {
        Map<String, Object> result = new HashMap<>();

        TantoEditor editor = tantoEditorRepository.findById(tantouId).orElse(null);
        if (editor == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Tantou!");
            return result;
        }

        List<Proposal> list = proposalRepository.findByStatusAndMangaka_Editor_Id("new", editor.getId());

        result.put("status", "success");
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Danh sách đề xuất đã được Tantou duyệt, chờ nộp lên hội đồng")
    @GetMapping("/approved-proposals")
    @ResponseBody
    public Map<String, Object> getApprovedProposals() {
        Map<String, Object> result = new HashMap<>();
        List<Proposal> list = proposalRepository.findByStatus("approved");
        result.put("status", "success");
        result.put("total", list.size());
        result.put("proposals", list);
        return result;
    }

    @Operation(summary = "Duyệt bản thảo: được duyệt / cần sửa / từ chối")
    @PostMapping("/review")
    @ResponseBody
    public Map<String, String> tantouReview(
            @RequestParam String id,
            @RequestParam String action, // "approve" | "revision" | "reject"
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Proposal p = proposalRepository.findById(id).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + id);
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null || p.getMangaka() == null || p.getMangaka().getEditor() == null
                || !editor.getId().equals(p.getMangaka().getEditor().getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không phải tantou phụ trách mangaka này!");
            return result;
        }

        if (!"new".equals(p.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này không ở trạng thái chờ duyệt!");
            return result;
        }

        // Từ chối bắt buộc phải có lý do để mangaka biết vì sao bị từ chối.
        if ("reject".equals(action) && (comment == null || comment.isBlank())) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập lý do khi từ chối đề xuất!");
            return result;
        }

        p.setComment(comment);
        p.setEditorScore(null);
        p.setReviewedAt(LocalDateTime.now());

        switch (action) {
            case "approve" -> {
                p.setStatus("approved");
                String commentText = (comment != null && !comment.isBlank()) ? " Nhận xét: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất \"" + p.getSeriesName() + "\" đã được biên tập viên duyệt và sẽ sớm gửi lên hội đồng."
                                + commentText,
                        "/manga/mangaka/my-projects");
            }
            case "revision" -> {
                if (deadline == null) {
                    result.put("status", "error");
                    result.put("message", "Vui lòng chọn deadline sửa bài!");
                    return result;
                }
                p.setStatus("revision");
                p.setRevisionDeadline(deadline);

                String deadlineText = deadline.toLocalDate() + " lúc " + deadline.toLocalTime();
                String commentText = (comment != null && !comment.isBlank()) ? " Góp ý: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Đề xuất \"" + p.getSeriesName() + "\" cần chỉnh sửa trước " + deadlineText + "." + commentText,
                        "/manga/mangaka/my-projects");
            }
            case "reject" -> {
                p.setStatus("locked");
                String commentText = (comment != null && !comment.isBlank()) ? " Lý do: " + comment.trim() : "";
                notificationController.send(null, p.getMangaka().getUser().getId(),
                        "Rất tiếc, đề xuất \"" + p.getSeriesName() + "\" đã bị từ chối." + commentText,
                        "/manga/mangaka/my-projects");
            }
            default -> {
                result.put("status", "error");
                result.put("message", "Hành động không hợp lệ!");
                return result;
            }
        }

        proposalRepository.save(p);
        result.put("status", "success");
        result.put("proposalId", id);
        result.put("newStatus", p.getStatus());

        // ✅ THÊM: set message phù hợp theo action để trả về cho frontend
        String successMessage = switch (action) {
            case "approve" -> "Đã duyệt đề xuất \"" + p.getSeriesName() + "\"!";
            case "revision" -> "Đã yêu cầu sửa lại đề xuất \"" + p.getSeriesName() + "\"!";
            case "reject" -> "Đã từ chối đề xuất \"" + p.getSeriesName() + "\"!";
            default -> "Cập nhật thành công!";
        };
        result.put("message", successMessage);

        return result;
    }

    @Operation(summary = "Nộp đề xuất đã duyệt lên hội đồng (kèm hồ sơ đề xuất)")
    @PostMapping(value = "/submit-to-board", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> submitToBoard(
            @RequestParam String proposalId,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Proposal p = proposalRepository.findById(proposalId).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null || p.getMangaka() == null || p.getMangaka().getEditor() == null
                || !editor.getId().equals(p.getMangaka().getEditor().getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không phải tantou phụ trách mangaka này!");
            return result;
        }

        if (!"approved".equals(p.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ đề xuất đã được duyệt mới có thể nộp lên hội đồng!");
            return result;
        }

        // Hội đồng sẽ xem trực tiếp bản thảo do mangaka nộp (p.filePath). Không cần
        // tantou tải lên "hồ sơ đề xuất" riêng nữa.
        p.setStatus("board_check");
        p.setBoardSubmittedAt(LocalDateTime.now());
        proposalRepository.save(p);

        notificationController.send("board", null,
                "Có dự án mới '" + p.getSeriesName() + "' cần bỏ phiếu!", "/manga/editor");

        result.put("status", "success");
        result.put("proposalId", proposalId);
        result.put("newStatus", p.getStatus());
        result.put("message", "Đã nộp lên hội đồng!");
        return result;
    }

    // ================== MỚI THÊM CHO TÍNH NĂNG 2 ==================
    @GetMapping("/my-mangakas")
    @ResponseBody
    public Map<String, Object> getMyMangakas(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null) {
            result.put("status", "error");
            result.put("message", "Không phải Tanto Editor!");
            return result;
        }

        List<Mangaka> list = mangakaRepository.findByEditor_Id(editor.getId());
        List<Map<String, Object>> data = list.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("mangakaId", m.getId());
            map.put("name", m.getUser().getFullname());
            map.put("email", m.getUser().getEmail());
            return map;
        }).collect(Collectors.toList());

        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    @GetMapping("/mangaka/{mangakaId}/series")
    @ResponseBody
    public Map<String, Object> getMangakaSeries(@PathVariable String mangakaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        List<Series> list = seriesRepository.findByProposal_Mangaka_Id(mangakaId);
        List<Map<String, Object>> data = list.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("seriesId", s.getId());
            map.put("seriesName", s.getSeriesName());
            map.put("status", s.getStatus());
            map.put("startDate", s.getStartDate());

            int totalView = likeResultRepository.findBySeries_Id(s.getId())
                    .stream().mapToInt(com.example.manga_management.entity.LikeResult::getViewCount).sum();
            map.put("viewCount", totalView);
            return map;
        }).collect(Collectors.toList());

        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    @Operation(summary = "Tantou sửa deadline chapter chưa nộp (kèm giờ, không được ở quá khứ)")
    @PostMapping("/chapters/{chapterId}/deadline")
    @ResponseBody
    public Map<String, Object> updateChapterDeadline(@PathVariable String chapterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
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

        if (!"unfinish".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể sửa deadline khi chapter chưa được nộp (trạng thái 'unfinish')!");
            return result;
        }

        if (!deadline.isAfter(LocalDateTime.now())) {
            result.put("status", "error");
            result.put("message", "Deadline phải sau thời điểm hiện tại, không được đặt vào quá khứ!");
            return result;
        }

        chapter.setDeadline(deadline);
        chapterRepository.save(chapter);

        result.put("status", "success");
        result.put("message", "Đã cập nhật deadline chapter '" + chapter.getChapterName() + "'!");
        return result;
    }

    @Operation(summary = "Danh sách series đang chờ hồ sơ bảo vệ (pending_cancel) của các mangaka do tantou này phụ trách")
    @GetMapping("/pending-cancel-series")
    @ResponseBody
    public Map<String, Object> getPendingCancelSeries(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        TantoEditor editor = tantoEditorRepository.findByUser(user).orElse(null);
        if (editor == null) {
            result.put("status", "error");
            result.put("message", "Không phải Tanto Editor!");
            return result;
        }

        List<Series> list = seriesRepository.findByStatusAndProposal_Mangaka_Editor_Id("pending_cancel", editor.getId());
        List<Map<String, Object>> data = list.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("seriesId", s.getId());
            map.put("seriesName", s.getSeriesName());
            map.put("mangakaName", s.getProposal().getMangaka().getUser().getFullname());

            VoteSession stopSession = voteSessionRepository
                    .findFirstBySeriesIdAndVoteTypeAndStatus(s.getId(), "stop", "closed").orElse(null);
            map.put("reason", stopSession != null ? stopSession.getReason() : null);

            VoteSession activeDefense = voteSessionRepository
                    .findFirstBySeriesIdAndVoteTypeAndStatus(s.getId(), "defense", "active")
                    .orElse(null);
            boolean hasActiveDefense = activeDefense != null;
            map.put("hasActiveDefense", hasActiveDefense);
            map.put("defenseSubmittedAt", activeDefense != null ? activeDefense.getCreatedAt() : null);
            return map;
        }).collect(Collectors.toList());

        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    @Operation(summary = "Tantou nộp hồ sơ bảo vệ (PDF + ghi chú) cho series đang pending_cancel")
    @PostMapping(value = "/series/{seriesId}/submit-defense", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> submitDefense(@PathVariable String seriesId,
            @RequestParam(required = false) String note,
            @RequestPart MultipartFile fileDefense,
            HttpSession session) {
        Map<String, String> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        if (series.getProposal() == null || series.getProposal().getMangaka() == null
                || series.getProposal().getMangaka().getEditor() == null
                || series.getProposal().getMangaka().getEditor().getUser() == null
                || !series.getProposal().getMangaka().getEditor().getUser().getId().equals(user.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không phải tantou phụ trách series này!");
            return result;
        }

        if (!"pending_cancel".equals(series.getStatus())) {
            result.put("status", "error");
            result.put("message", "Series này không ở trạng thái chờ hồ sơ bảo vệ!");
            return result;
        }

        if (voteSessionRepository.existsBySeriesIdAndStatus(seriesId, "active")) {
            result.put("status", "error");
            result.put("message", "Series này đã có hồ sơ bảo vệ đang chờ hội đồng bỏ phiếu!");
            return result;
        }

        if (fileDefense.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file PDF hồ sơ bảo vệ!");
            return result;
        }

        String fileName = fileDefense.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            result.put("status", "error");
            result.put("message", "Chỉ được phép tải lên file PDF!");
            return result;
        }

        try {
            String workingDir = System.getProperty("user.dir");
            String uploadDir = workingDir + File.separator + "src" + File.separator + "main" + File.separator
                    + "resources" + File.separator + "static" + File.separator + "series-defense" + File.separator;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String sessionId = generateSessionId();
            String savedFileName = sessionId + ".pdf";
            fileDefense.transferTo(uploadPath.resolve(savedFileName).toFile());

            VoteSession vs = new VoteSession();
            vs.setId(sessionId);
            vs.setSeries(series);
            vs.setCreatedBy(null);
            vs.setVoteType("defense");
            vs.setStatus("active");
            vs.setCreatedAt(LocalDate.now());
            vs.setAutoCreated(false);
            vs.setDefenseFilePath("/series-defense/" + savedFileName);
            vs.setDefenseNote(note != null ? note.trim() : null);
            voteSessionRepository.save(vs);

            notificationController.send("board", null,
                    "Series '" + series.getSeriesName() + "' đã nộp hồ sơ bảo vệ, cần bỏ phiếu!",
                    "/manga/editor");

            // Báo cho tác giả biết tantou đã nộp hồ sơ bảo vệ — trước đây mangaka chỉ
            // biết khi có kết quả cuối cùng, không biết ai đó đã xử lý ở giữa chừng.
            if (series.getProposal() != null && series.getProposal().getMangaka() != null
                    && series.getProposal().getMangaka().getUser() != null) {
                notificationController.send(null,
                        series.getProposal().getMangaka().getUser().getId(),
                        "Tantou đã nộp hồ sơ bảo vệ cho series '" + series.getSeriesName()
                                + "', đang chờ hội đồng bỏ phiếu.",
                        "/manga/mangaka");
            }

            result.put("status", "success");
            result.put("sessionId", sessionId);
            result.put("message", "Đã nộp hồ sơ bảo vệ, chờ hội đồng bỏ phiếu!");
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    private String generateSessionId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        java.util.Random rand = new java.util.Random();
        StringBuilder sb = new StringBuilder("VS");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }
}
