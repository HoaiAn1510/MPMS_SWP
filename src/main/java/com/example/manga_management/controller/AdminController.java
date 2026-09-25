package com.example.manga_management.controller;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.ActivityLog;
import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.BoardProposalComment;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.LikeResult;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.PublicDate;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.SeriesVote;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.ActivityLogRepository;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.BoardProposalCommentRepository;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.LikeResultRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.NotificationRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.PublicDateRepository;
import com.example.manga_management.repository.RankingRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SeriesVoteRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.UserRepository;
import com.example.manga_management.repository.VoteSessionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/system-admin")
@Tag(name = "Admin", description = "Quản trị hệ thống: tài khoản, phân công, xuất bản chapter, xếp hạng, lịch sử hoạt động, import Excel")
public class AdminController {

    private final UserRepository userRepository;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final BoardRepository boardRepository;
    private final SeriesRepository seriesRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final RankingRepository rankingRepository;
    private final BoardProposalCommentRepository boardProposalCommentRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationRepository notificationRepository;
    private final ProposalRepository proposalRepository;
    private final ChapterRepository chapterRepository;
    private final LikeResultRepository likeResultRepository;
    private final PublicDateRepository publicDateRepository;
    private final NotificationController notificationController;
    private final ActivityLogRepository activityLogRepository;

    public AdminController(UserRepository userRepository,
            MangakaRepository mangakaRepository,
            AssistantRepository assistantRepository,
            TantoEditorRepository tantoEditorRepository,
            BoardRepository boardRepository,
            SeriesRepository seriesRepository,
            VoteSessionRepository voteSessionRepository,
            RankingRepository rankingRepository,
            BoardProposalCommentRepository boardProposalCommentRepository,
            SeriesVoteRepository seriesVoteRepository,
            SubmissionRepository submissionRepository,
            NotificationRepository notificationRepository,
            ProposalRepository proposalRepository,
            ChapterRepository chapterRepository,
            LikeResultRepository likeResultRepository,
            PublicDateRepository publicDateRepository,
            NotificationController notificationController,
            ActivityLogRepository activityLogRepository) {
        this.userRepository = userRepository;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.boardRepository = boardRepository;
        this.seriesRepository = seriesRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.rankingRepository = rankingRepository;
        this.boardProposalCommentRepository = boardProposalCommentRepository;
        this.seriesVoteRepository = seriesVoteRepository;
        this.submissionRepository = submissionRepository;
        this.notificationRepository = notificationRepository;
        this.proposalRepository = proposalRepository;
        this.chapterRepository = chapterRepository;
        this.likeResultRepository = likeResultRepository;
        this.publicDateRepository = publicDateRepository;
        this.notificationController = notificationController;
        this.activityLogRepository = activityLogRepository;
    }

    @GetMapping("")
    public String adminPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            return "redirect:/login";
        }
        model.addAttribute("currentUserId", user.getId());
        return "admin";
    }

    @Operation(summary = "Dữ liệu tổng quan dashboard admin (thống kê tài khoản, series, đề xuất...)")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> adminData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        result.put("status", "success");
        result.put("allUsers", userRepository.findAll());
        result.put("tantoEditors", tantoEditorRepository.findAll());
        result.put("boards", boardRepository.findAll());

        // Build mangakas với editor info đầy đủ
        List<Map<String, Object>> mangakaList = new ArrayList<>();
        for (Mangaka m : mangakaRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("user", m.getUser());
            item.put("salaryPerChapter", m.getSalaryPerChapter());
            if (m.getEditor() != null) {
                Map<String, Object> editorMap = new HashMap<>();
                editorMap.put("id", m.getEditor().getId());
                editorMap.put("user", m.getEditor().getUser());
                item.put("editor", editorMap);
            }
            mangakaList.add(item);
        }
        result.put("mangakas", mangakaList);

        // Build assistants với mangaka.user đầy đủ
        List<Map<String, Object>> assistantList = new ArrayList<>();
        for (Assistant a : assistantRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("user", a.getUser());
            item.put("salaryPerTask", a.getSalaryPerTask());
            if (a.getMangaka() != null) {
                Map<String, Object> mgkMap = new HashMap<>();
                mgkMap.put("id", a.getMangaka().getId());
                mgkMap.put("user", a.getMangaka().getUser());
                item.put("mangaka", mgkMap);
            }
            assistantList.add(item);
        }
        result.put("assistants", assistantList);

        return result;
    }

    @Operation(summary = "Danh sách chapter đã duyệt (pass), đang chờ admin xuất bản")
    @GetMapping("/chapters/pending-publish")
    @ResponseBody
    public Map<String, Object> getChaptersPendingPublish(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Chapter c : chapterRepository.findByStatus("pass")) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("chapterName", c.getChapterName());
            item.put("chapterNumber", c.getChapterNumber());
            item.put("seriesId", c.getSeries().getId());
            item.put("seriesName", c.getSeries().getSeriesName());
            chapters.add(item);
        }

        result.put("status", "success");
        result.put("chapters", chapters);
        return result;
    }

    @Operation(summary = "Xuất bản 1 chapter (thông báo cho mangaka, tantou và toàn bộ board)")
    @PostMapping("/chapters/{chapterId}/publish")
    @ResponseBody
    public Map<String, Object> publishChapter(@PathVariable String chapterId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }
        if (!"pass".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể xuất bản chapter đã được Tantou duyệt (trạng thái 'pass')");
            return result;
        }

        chapter.setStatus("published");
        chapterRepository.save(chapter);

        // Khi xuất bản chapter đầu tiên của series, series đang ở trạng thái đang
        // thực hiện ("unfinish"/"new") sẽ chuyển sang "published" (đã phát hành).
        // Không đụng tới series đã completed/stopped/pending_cancel.
        Series seriesOfChapter = chapter.getSeries();
        if (seriesOfChapter != null
                && ("unfinish".equals(seriesOfChapter.getStatus())
                || "new".equals(seriesOfChapter.getStatus()))) {
            seriesOfChapter.setStatus("published");
            seriesRepository.save(seriesOfChapter);
        }

        String lastPublicId = publicDateRepository.findTopByOrderByIdDesc()
                .map(PublicDate::getId).orElse("PUB000");
        int nextPublicNum = Integer.parseInt(lastPublicId.replaceAll("[^0-9]", "")) + 1;
        PublicDate publicDate = new PublicDate();
        publicDate.setId(String.format("PUB%03d", nextPublicNum));
        publicDate.setChapter(chapter);
        publicDate.setDatePublic(LocalDate.now());
        publicDateRepository.save(publicDate);

        // Báo cho mangaka, tantou phụ trách, và toàn bộ hội đồng board biết chapter đã lên sóng
        if (chapter.getSeries() != null && chapter.getSeries().getProposal() != null
                && chapter.getSeries().getProposal().getMangaka() != null) {
            var mangaka = chapter.getSeries().getProposal().getMangaka();
            String content = "🎉 Chapter '" + chapter.getChapterName() + "' của series '"
                    + chapter.getSeries().getSeriesName() + "' đã được xuất bản!";
            if (mangaka.getUser() != null) {
                notificationController.send(null, mangaka.getUser().getId(), content, "/manga/mangaka");
            }
            if (mangaka.getEditor() != null && mangaka.getEditor().getUser() != null) {
                notificationController.send(null, mangaka.getEditor().getUser().getId(), content, "/manga/tantou");
            }
            notificationController.send("board", null, content, "/manga/editor");
        }

        result.put("status", "success");
        result.put("message", "Đã xuất bản chapter '" + chapter.getChapterName() + "'!");
        return result;
    }

    @Operation(summary = "Danh sách series đã xuất bản (đang phát hành / đã hoàn thành)")
    @GetMapping("/series/published")
    @ResponseBody
    public Map<String, Object> getPublishedSeries(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        // "Đã xuất bản" = series đã có ít nhất 1 chapter published, hoặc series đã
        // hoàn thành. KHÔNG chỉ dựa vào series.status vì dữ liệu cũ (chapter được
        // xuất bản trước khi có tính năng tự nâng trạng thái) có thể vẫn đang
        // "unfinish" dù đã phát hành — ở đây tự sửa (backfill) lại cho đúng.
        for (Series s : seriesRepository.findAll()) {
            long publishedCount = chapterRepository.findBySeries_IdAndStatus(s.getId(), "published").size();
            boolean isCompleted = "completed".equals(s.getStatus());
            if (publishedCount == 0 && !isCompleted) {
                continue;
            }

            // Backfill: series đã phát hành nhưng còn ở trạng thái đang thực hiện.
            if (publishedCount > 0
                    && ("unfinish".equals(s.getStatus()) || "new".equals(s.getStatus()))) {
                s.setStatus("published");
                seriesRepository.save(s);
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("seriesName", s.getSeriesName());
            map.put("status", s.getStatus());
            map.put("genre", s.getGenre());
            map.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
            map.put("mangakaName",
                    s.getProposal() != null && s.getProposal().getMangaka() != null
                    && s.getProposal().getMangaka().getUser() != null
                    ? s.getProposal().getMangaka().getUser().getFullname() : "—");
            map.put("publishedChapters", publishedCount);
            list.add(map);
        }

        result.put("status", "success");
        result.put("series", list);
        return result;
    }

    @Operation(summary = "Danh sách chapter của 1 series (kèm trạng thái) cho admin xem")
    @GetMapping("/series/{seriesId}/chapters")
    @ResponseBody
    public Map<String, Object> getSeriesChapters(@PathVariable String seriesId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
        chapters.sort(java.util.Comparator.comparing(
                c -> c.getChapterNumber() != null ? c.getChapterNumber() : Integer.MAX_VALUE));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Chapter c : chapters) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("chapterName", c.getChapterName());
            map.put("chapterNumber", c.getChapterNumber());
            map.put("status", c.getStatus());
            map.put("statusLabel", chapterStatusLabel(c.getStatus()));
            String publishDate = publicDateRepository.findTopByChapter_IdOrderByDatePublicDesc(c.getId())
                    .map(pd -> pd.getDatePublic().toString()).orElse(null);
            map.put("publishDate", publishDate);
            list.add(map);
        }

        result.put("status", "success");
        result.put("seriesName", seriesOpt.get().getSeriesName());
        result.put("seriesStatus", seriesOpt.get().getStatus());
        result.put("chapters", list);
        return result;
    }

    /** Nhãn tiếng Việt cho trạng thái chapter, dùng khi admin xem danh sách chapter. */
    private String chapterStatusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "new" -> "Mới tạo";
            case "unfinish" -> "Đang thực hiện";
            case "intask" -> "Đang giao việc";
            case "finish" -> "Chờ Tantou duyệt";
            case "revision" -> "Yêu cầu chỉnh sửa";
            case "pass" -> "Đã duyệt, chờ xuất bản";
            case "published" -> "Đã xuất bản";
            case "stopped", "cancelled" -> "Đã dừng";
            case "locked" -> "Đang khoá";
            default -> status;
        };
    }

    @Operation(summary = "Tạo tài khoản mới (mangaka/assistant/tantou/board/admin)")
    @PostMapping("/accounts/create")
    @ResponseBody
    public Map<String, Object> createAccount(@RequestBody Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Kiểm tra quyền
            User admin = (User) session.getAttribute("user");
            if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
                result.put("status", "error");
                result.put("message", "Không có quyền");
                return result;
            }

            // Kiểm tra username trùng
            if (userRepository.findByUsername(body.get("username")).isPresent()) {
                result.put("status", "error");
                result.put("message", "Username đã tồn tại");
                return result;
            }

            String role = body.get("role");

            // Kiểm tra trước các ràng buộc riêng theo role (TantoEditor/Mangaka phải
            // tồn tại) TRƯỚC khi lưu User — tránh tạo ra tài khoản "mồ côi" (User đã
            // lưu nhưng profile tương ứng lưu thất bại) như trước đây.
            TantoEditor editorForMangaka = null;
            Mangaka mangakaForAssistant = null;
            if ("mangaka".equalsIgnoreCase(role)) {
                String editorId = body.get("editorId");
                editorForMangaka = editorId != null ? tantoEditorRepository.findById(editorId).orElse(null) : null;
                if (editorForMangaka == null) {
                    result.put("status", "error");
                    result.put("message", "Không tìm thấy TantoEditor với ID: " + editorId);
                    return result;
                }
            } else if ("assistant".equalsIgnoreCase(role)) {
                String mangakaId = body.get("mangakaId");
                mangakaForAssistant = mangakaId != null ? mangakaRepository.findById(mangakaId).orElse(null) : null;
                if (mangakaForAssistant == null) {
                    result.put("status", "error");
                    result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
                    return result;
                }
            }

            // Tạo User — dựa trên ID lớn nhất hiện có, không dùng count()+1 (count()+1
            // sẽ sinh trùng ID cũ sau khi có tài khoản bị xóa, gây đè dữ liệu tài
            // khoản khác đang tồn tại).
            String lastUserId = userRepository.findTopByOrderByIdDesc().map(User::getId).orElse("000000");
            String newId = String.format("%06d", Long.parseLong(lastUserId) + 1);
            User newUser = new User();
            newUser.setId(newId);
            newUser.setUsername(body.get("username"));
            newUser.setPassword(body.get("password"));
            newUser.setFullname(body.getOrDefault("fullname", ""));
            newUser.setEmail(body.getOrDefault("email", ""));
            newUser.setRole(role);
            userRepository.save(newUser);

            // Tạo profile tương ứng với role
            String profileId = nextProfileId(role);

            switch (role.toLowerCase()) {
                case "mangaka" -> {
                    Mangaka mangaka = new Mangaka();
                    mangaka.setId(profileId);
                    mangaka.setUser(newUser);
                    mangaka.setEditor(editorForMangaka);
                    mangaka.setSalaryPerChapter(
                            Integer.parseInt(body.getOrDefault("salaryPerChapter", "0")));
                    mangakaRepository.save(mangaka);
                }
                case "assistant" -> {
                    Assistant assistant = new Assistant();
                    assistant.setId(profileId);
                    assistant.setUser(newUser);
                    assistant.setMangaka(mangakaForAssistant);
                    assistant.setSalaryPerTask(
                            Integer.parseInt(body.getOrDefault("salaryPerTask", "0")));
                    assistantRepository.save(assistant);
                }
                case "tantou" -> {
                    TantoEditor editor = new TantoEditor();
                    editor.setId(profileId);
                    editor.setUser(newUser);
                    tantoEditorRepository.save(editor);
                }
                case "board" -> {
                    Board board = new Board();
                    board.setId(profileId);
                    board.setUser(newUser);
                    boardRepository.save(board);
                }
                // "admin" không cần profile riêng
            }

            result.put("status", "success");
            result.put("user", newUser);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Cập nhật thông tin tài khoản")
    @PostMapping("/accounts/update")
    @ResponseBody
    public Map<String, Object> updateAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            User u = userRepository.findById(body.get("id")).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + body.get("id"));
                return result;
            }

            if (isSet(body, "username")) {
                u.setUsername(body.get("username"));
            }
            if (isSet(body, "fullname")) {
                u.setFullname(body.get("fullname"));
            }
            if (isSet(body, "email")) {
                u.setEmail(body.get("email"));
            }
            if (isSet(body, "password")) {
                u.setPassword(body.get("password"));
            }
            // Không cho đổi role vì liên quan đến profile table

            userRepository.save(u);

            // Nếu là assistant và muốn đổi mangaka
            if (isSet(body, "mangakaId")) {
                Assistant assistant = assistantRepository.findByUserId(u.getId()).orElse(null);
                Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
                if (assistant != null && mangaka != null) {
                    assistant.setMangaka(mangaka);
                    if (isSet(body, "salaryPerTask")) {
                        assistant.setSalaryPerTask(Integer.parseInt(body.get("salaryPerTask")));
                    }
                    assistantRepository.save(assistant);
                }
            }

            // Nếu là mangaka và muốn đổi tantou
            if (isSet(body, "editorId")) {
                Mangaka mangaka = mangakaRepository.findByUserId(u.getId()).orElse(null);
                TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);
                if (mangaka != null && editor != null) {
                    mangaka.setEditor(editor);
                    mangakaRepository.save(mangaka);
                }
            }

            // Nếu là mangaka và muốn đổi lương mỗi chapter
            if (isSet(body, "salaryPerChapter")) {
                Mangaka mangaka = mangakaRepository.findByUserId(u.getId()).orElse(null);
                if (mangaka != null) {
                    mangaka.setSalaryPerChapter(Integer.parseInt(body.get("salaryPerChapter")));
                    mangakaRepository.save(mangaka);
                }
            }

            result.put("status", "success");
            result.put("user", u);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Xóa tài khoản")
    @PostMapping("/accounts/delete")
    @ResponseBody
    public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String id = body.get("id");
            User u = userRepository.findById(id).orElse(null);
            if (u == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy user với ID: " + id);
                return result;
            }

            // Xóa profile trước, sau đó xóa user
            switch (u.getRole().toLowerCase()) {
                case "mangaka" ->
                    mangakaRepository.findByUserId(id)
                            .ifPresent(mangakaRepository::delete);
                case "assistant" ->
                    assistantRepository.findByUserId(id)
                            .ifPresent(assistantRepository::delete);
                case "tantou" ->
                    tantoEditorRepository.findByUserId(id)
                            .ifPresent(tantoEditorRepository::delete);
                case "board" ->
                    boardRepository.findByUser_Id(id)
                            .ifPresent(boardRepository::delete);
            }

            userRepository.deleteById(id);
            result.put("status", "success");
            result.put("deletedId", id);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ĐỔI QUAN HỆ (riêng biệt với update thông tin cơ bản)
    // ══════════════════════════════════════════════════════════════
    // Đổi Mangaka của Assistant
    @Operation(summary = "Gán 1 trợ lý vào làm việc dưới 1 mangaka")
    @PostMapping("/assign/assistant-mangaka")
    @ResponseBody
    public Map<String, Object> assignAssistantToMangaka(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Assistant assistant = assistantRepository.findById(body.get("assistantId")).orElse(null);
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);

            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Assistant");
                return result;
            }
            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }

            if ("intask".equalsIgnoreCase(assistant.getStatus())) {
                result.put("status", "error");
                result.put("message", "Assistant này đang có task từ Mangaka, Vui lòng thử lại sau!");
                return result;
            }

            assistant.setMangaka(mangaka);
            assistantRepository.save(assistant);

            result.put("status", "success");
            result.put("assistantId", assistant.getId());
            result.put("assistantName", assistant.getUser().getFullname());
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // Đổi TantoEditor của Mangaka
    @Operation(summary = "Gán 1 mangaka vào phụ trách bởi 1 tantou (biên tập viên)")
    @PostMapping("/assign/mangaka-tanto")
    @ResponseBody
    public Map<String, Object> assignMangakaToTanto(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Mangaka mangaka = mangakaRepository.findById(body.get("mangakaId")).orElse(null);
            TantoEditor editor = tantoEditorRepository.findById(body.get("editorId")).orElse(null);

            if (mangaka == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy Mangaka");
                return result;
            }
            if (editor == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy TantoEditor");
                return result;
            }

            mangaka.setEditor(editor);
            mangakaRepository.save(mangaka);

            result.put("status", "success");
            result.put("mangakaId", mangaka.getId());
            result.put("mangakaName", mangaka.getUser().getFullname());
            result.put("editorId", editor.getId());
            result.put("editorName", editor.getUser().getFullname());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // QUẢN LÝ VOTE SERIES (Admin tạo phiên, Board cast vote)
    // ════════════════════════════════════════════════════════════
    // Lấy danh sách series cho dropdown khi tạo phiên
    @Operation(summary = "Danh sách toàn bộ series để admin chọn tạo phiên vote")
    @GetMapping("/ranking/all-series")
    @ResponseBody
    public Map<String, Object> getAllSeriesForVote(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        List<Object[]> rows = rankingRepository.findAllSeriesDistinct();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row[0]);
            map.put("name", row[1]);
            map.put("status", row[2]);
            list.add(map);
        }
        result.put("status", "success");
        result.put("series", list);
        return result;
    }

    // Tạo phiên vote thủ công (Admin chọn series + loại vote)
    @Operation(summary = "Admin mở phiên vote mới (stop/defense/reward) cho 1 series")
    @PostMapping("/ranking/create-session")
    @ResponseBody
    public Map<String, Object> createVoteSession(
            @RequestParam String seriesId,
            @RequestParam String voteType,
            @RequestParam(required = false) String reason,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            response.put("success", false);
            response.put("message", "Không có quyền truy cập");
            return response;
        }

        if (!voteType.equals("stop") && !voteType.equals("reward")) {
            response.put("success", false);
            response.put("message", "Loại vote không hợp lệ (stop hoặc reward)!");
            return response;
        }

        Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy series!");
            return response;
        }

        String seriesStatus = seriesOpt.get().getStatus();
        if ("stopped".equals(seriesStatus)) {
            response.put("success", false);
            response.put("message", "Series này đã bị dừng phát hành, không thể tạo phiên vote mới!");
            return response;
        }
        if ("pending_cancel".equals(seriesStatus)) {
            response.put("success", false);
            response.put("message",
                    "Series đang trong diện xem xét dừng (chờ hồ sơ bảo vệ), không thể tạo phiên vote mới!");
            return response;
        }
        // Series đã hoàn thành thì không đưa vào diện vote dừng nữa.
        if ("stop".equals(voteType) && "completed".equals(seriesStatus)) {
            response.put("success", false);
            response.put("message", "Series đã hoàn thành, không thể tạo phiên vote dừng!");
            return response;
        }

        if ("stop".equals(voteType) && (reason == null || reason.isBlank())) {
            response.put("success", false);
            response.put("message", "Vui lòng nhập lý do khi tạo phiên vote dừng series!");
            return response;
        }

        // Reward: chỉ tạo được phiên vote mới nếu series còn ít nhất 1 chapter đã
        // published mà CHƯA từng được đánh dấu khen thưởng (isReward=false).
        // Không dựa vào status series vì thưởng không đổi status series nữa —
        // series có thể được xét thưởng nhiều lần, mỗi lần chỉ tính cho các
        // chapter mới publish thêm kể từ lần thưởng trước.
        if ("reward".equals(voteType)) {
            boolean hasUnrewardedChapter = chapterRepository
                    .findBySeries_IdAndStatus(seriesId, "published")
                    .stream()
                    .anyMatch(c -> !c.isReward());
            if (!hasUnrewardedChapter) {
                response.put("success", false);
                response.put("message",
                        "Series này không còn chapter nào đã xuất bản mà chưa được khen thưởng, không thể tạo phiên vote khen thưởng!");
                return response;
            }
        }

        // Chỉ chặn khi series đang có phiên vote MỞ. Phiên cũ đã đóng (vd series
        // từng bị vote dừng nhưng bảo vệ thành công) thì sau này vẫn được xét lại.
        if (voteSessionRepository.existsBySeriesIdAndStatus(seriesId, "active")) {
            response.put("success", false);
            response.put("message", "Series này đang có phiên vote đang mở rồi! Phải đóng phiên hiện tại trước.");
            return response;
        }

        VoteSession vs = new VoteSession();
        vs.setId(generateSessionId());
        vs.setSeries(seriesOpt.get());
        vs.setCreatedBy(null); // Admin tạo, không phải Board
        vs.setVoteType(voteType);
        vs.setStatus("active");
        vs.setCreatedAt(LocalDate.now());
        vs.setAutoCreated(false);
        vs.setReason(reason != null ? reason.trim() : null);
        voteSessionRepository.save(vs);

        response.put("success", true);
        response.put("sessionId", vs.getId());
        response.put("message", "Đã tạo phiên vote " + (voteType.equals("stop") ? "dừng" : "khen thưởng")
                + " cho series \"" + seriesOpt.get().getSeriesName() + "\"!");
        return response;
    }

    // Tạo phiên vote dừng cho 3 series cuối bảng
    @Operation(summary = "Tự động mở phiên vote 'stop' cho các series cuối bảng xếp hạng")
    @PostMapping("/ranking/create-bottom-sessions")
    @ResponseBody
    public Map<String, Object> createBottomVoteSessions(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int quarter,
            @RequestParam(defaultValue = "2026") int year,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            response.put("success", false);
            response.put("message", "Không có quyền truy cập");
            return response;
        }

        try {
            List<Object[]> rows;
            if (quarter >= 1 && quarter <= 4) {
                List<Integer> months = getMonthsForQuarter(quarter);
                rows = rankingRepository.findBottomByQuarterAndYear(months, year);
            } else if (month > 0) {
                rows = rankingRepository.findBottomByMonthAndYear(month, year);
            } else {
                rows = rankingRepository.findBottomByYear(year);
            }

            int count = Math.min(3, rows.size());
            List<String> createdNames = new ArrayList<>();
            List<String> skippedNames = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String sid = (String) rows.get(i)[0];
                String sname = (String) rows.get(i)[1];

                Optional<Series> seriesOpt = seriesRepository.findById(sid);
                if (seriesOpt.isEmpty()) {
                    continue;
                }
                String seriesStatus = seriesOpt.get().getStatus();

                // Bỏ qua series đã có kết quả cuối, đã hoàn thành, đang chờ bảo vệ,
                // hoặc đang có phiên vote mở. Phiên cũ đã đóng thì vẫn xét dừng lại.
                if ("stopped".equals(seriesStatus)
                        || "completed".equals(seriesStatus)
                        || "pending_cancel".equals(seriesStatus)
                        || voteSessionRepository.existsBySeriesIdAndStatus(sid, "active")) {
                    skippedNames.add(sname);
                    continue;
                }

                VoteSession vs = new VoteSession();
                vs.setId(generateSessionId());
                vs.setSeries(seriesOpt.get());
                vs.setCreatedBy(null); // Admin tạo
                vs.setVoteType("stop");
                vs.setStatus("active");
                vs.setCreatedAt(LocalDate.now());
                vs.setAutoCreated(false);
                vs.setReason("Nằm trong 3 series có lượt vote thấp nhất kỳ xếp hạng đang chọn.");
                voteSessionRepository.save(vs);
                createdNames.add(sname);
            }

            String message;
            if (skippedNames.isEmpty()) {
                message = "Đã tạo phiên vote cho 3 series cuối bảng: "
                        + String.join(", ", createdNames) + "!";
            } else if (createdNames.isEmpty()) {
                message = "Cả 3 series cuối bảng (" + String.join(", ", skippedNames)
                        + ") đều đã có phiên vote trước đó, không tạo được phiên mới!";
            } else {
                message = "Series " + String.join(", ", skippedNames)
                        + " đã có phiên vote trước đó. Chỉ tạo được phiên vote cho: "
                        + String.join(", ", createdNames);
            }

            response.put("success", true);
            response.put("message", message);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi tạo phiên: " + e.getMessage());
        }
        return response;
    }

    private String generateSessionId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("VS");
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════
    // LỊCH SỬ HOẠT ĐỘNG USER (Admin xem)
    // ════════════════════════════════════════════════════════════
    // Search user theo keyword (fullname / username / email / id)
    @Operation(summary = "Tìm kiếm tài khoản theo tên/username/email/role")
    @GetMapping("/users/search")
    @ResponseBody
    public Map<String, Object> searchUsers(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String keyword,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        User admin = (User) session.getAttribute("user");
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        String kw = keyword.trim().toLowerCase();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            boolean match = kw.isEmpty()
                    || (u.getId() != null && u.getId().toLowerCase().contains(kw))
                    || (u.getFullname() != null && u.getFullname().toLowerCase().contains(kw))
                    || (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw));
            if (!match) {
                continue;
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("fullname", u.getFullname());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            list.add(m);
        }

        result.put("status", "success");
        result.put("users", list);
        return result;
    }

    // Lấy lịch sử hoạt động của 1 user — tổng hợp từ nhiều bảng có sẵn
    @Operation(summary = "Lịch sử hoạt động của 1 tài khoản — gộp từ nhiều nguồn (Proposal, Chapter, Submission, BoardProposalComment, ActivityLog)")
    @GetMapping("/users/{userId}/activity")
    @ResponseBody
    public Map<String, Object> getUserActivity(
            @org.springframework.web.bind.annotation.PathVariable String userId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        User admin = (User) session.getAttribute("user");
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy user");
            return result;
        }

        // Lịch sử HOẠT ĐỘNG = những việc chính user này ĐÃ LÀM (kèm mốc thời gian
        // thật của hành động). Không lấy Notification vì đó là tin nhắn user NHẬN
        // được, không phải hành động do user thực hiện.
        List<Map<String, Object>> activities = new ArrayList<>();
        String role = target.getRole() != null ? target.getRole().toLowerCase() : "";

        // 1. Board: bỏ phiếu bản thảo / bỏ phiếu series 
        if ("board".equals(role)) {
            // Phiếu bầu bản thảo thật sự nằm ở board_proposal_comment (BoardProposalComment)
            // — EditorialVote là bảng không có nơi nào ghi dữ liệu vào, không dùng.
            for (BoardProposalComment bc : boardProposalCommentRepository.findByBoard_User_IdOrderByCreatedAtDesc(userId)) {
                String label = "pass".equalsIgnoreCase(bc.getAction()) ? "chấp thuận" : "bác bỏ";
                addActivity(activities, bc.getCreatedAt(), "vote-proposal", "fa-file-circle-check",
                        "Đã bỏ phiếu " + label + " bản thảo \""
                        + (bc.getProposal() != null ? bc.getProposal().getSeriesName() : "—") + "\""
                        + (bc.getContent() != null && !bc.getContent().isBlank()
                        ? " — nhận xét: " + bc.getContent() : ""));
            }

            for (SeriesVote sv : seriesVoteRepository.findByBoard_User_IdOrderByVoteDateDesc(userId)) {
                String choice = sv.getVote() == null ? "" : sv.getVote().toLowerCase();
                String label = switch (choice) {
                    case "stop" ->
                        "đồng ý dừng";
                    case "keep" ->
                        "giữ lại";
                    case "reward" ->
                        "đồng ý khen thưởng";
                    case "against" ->
                        "không đồng ý khen thưởng";
                    // Phiên bảo vệ (defense) dùng approve/reject
                    case "approve" ->
                        "chấp nhận hồ sơ bảo vệ";
                    case "reject" ->
                        "bác bỏ hồ sơ bảo vệ";
                    default ->
                        choice;
                };
                Series votedSeries = sv.getSeries();
                addActivity(activities, sv.getVoteDate(), "vote-series", "fa-square-poll-vertical",
                        "Đã bỏ phiếu " + label + " cho series \""
                        + (votedSeries != null ? votedSeries.getSeriesName() : "—") + "\""
                        + (votedSeries != null && votedSeries.getId() != null
                        ? " (mã truyện: " + votedSeries.getId() + ")" : ""));
            }

            for (VoteSession vs : voteSessionRepository.findByCreatedBy_User_IdOrderByCreatedAtDesc(userId)) {
                String t = "stop".equalsIgnoreCase(vs.getVoteType()) ? "dừng" : "khen thưởng";
                addActivity(activities, vs.getCreatedAt(), "create-session", "fa-plus-square",
                        "Đã tạo phiên vote " + t + " cho series \""
                        + (vs.getSeries() != null ? vs.getSeries().getSeriesName() : "—") + "\"");
            }
        }

        // 2. Mangaka: nộp bản thảo, khởi động series, giao việc, duyệt bài trợ lý
        if ("mangaka".equals(role)) {
            Optional<Mangaka> mgkOpt = mangakaRepository.findByUserId(userId);
            if (mgkOpt.isPresent()) {
                String mangakaId = mgkOpt.get().getId();

                for (Proposal p : proposalRepository.findByMangaka_Id(mangakaId)) {
                    addActivity(activities, p.getCreatedAt(), "proposal", "fa-file-pen",
                            "Đã nộp bản thảo \"" + p.getSeriesName() + "\"");
                }

                for (Series s : seriesRepository.findByProposal_Mangaka_Id(mangakaId)) {
                    addActivity(activities, s.getStartDate(), "start-series", "fa-rocket",
                            "Đã khởi động series \"" + s.getSeriesName() + "\"");
                }

                for (Submission s : submissionRepository.findByAssistant_Mangaka_IdOrderByCreatedAtDesc(mangakaId)) {
                    String assistantName = s.getAssistant() != null && s.getAssistant().getUser() != null
                            ? s.getAssistant().getUser().getFullname()
                            : "—";
                    String pageInfo = describePage(s);

                    addActivity(activities, s.getCreatedAt(), "assign-task", "fa-list-check",
                            "Đã giao việc " + pageInfo + " cho " + assistantName);

                    addActivity(activities, s.getApprovedAt(), "approve-task", "fa-circle-check",
                            "Đã duyệt bài " + pageInfo + " của " + assistantName);
                }
            }
        }

        // 3. Assistant: được giao việc + nộp bài
        if ("assistant".equals(role)) {
            Optional<Assistant> astOpt = assistantRepository.findByUserId(userId);
            if (astOpt.isPresent()) {
                for (Submission s : submissionRepository.findByAssistant_Id(astOpt.get().getId())) {
                    String pageInfo = describePage(s);

                    addActivity(activities, s.getCreatedAt(), "receive-task", "fa-inbox",
                            "Được giao việc " + pageInfo
                            + (s.getDeadline() != null ? " (deadline " + s.getDeadline() + ")" : ""));

                    addActivity(activities, s.getSubmittedAt(), "submit-task", "fa-paper-plane",
                            "Đã nộp bài " + pageInfo);
                }
            }
        }

        // 4. Tantou: duyệt / yêu cầu sửa / từ chối / nộp bản thảo lên hội đồng
        if ("tantou".equals(role)) {
            Optional<TantoEditor> editorOpt = tantoEditorRepository.findByUserId(userId);
            if (editorOpt.isPresent()) {
                for (Proposal p : proposalRepository
                        .findByMangaka_Editor_IdOrderByCreatedAtDesc(editorOpt.get().getId())) {
                    if (p.getReviewedAt() == null) {
                        continue; // chưa xử lý bản thảo này thì không phải hoạt động của tantou
                    }
                    String mangakaName = p.getMangaka() != null && p.getMangaka().getUser() != null
                            ? p.getMangaka().getUser().getFullname()
                            : "—";
                    String action = switch (p.getStatus() != null ? p.getStatus().toLowerCase() : "") {
                        case "approved" ->
                            "Đã duyệt";
                        case "revision" ->
                            "Đã yêu cầu chỉnh sửa";
                        case "locked" ->
                            "Đã từ chối";
                        case "board_check", "passed", "started" ->
                            "Đã nộp lên hội đồng";
                        default ->
                            "Đã xử lý";
                    };
                    addActivity(activities, p.getReviewedAt(), "review-proposal", "fa-file-circle-check",
                            action + " bản thảo \"" + p.getSeriesName() + "\" của " + mangakaName);
                }

                for (Chapter c : chapterRepository
                        .findBySeries_Proposal_Mangaka_Editor_User_IdAndReviewedAtIsNotNullOrderByReviewedAtDesc(userId)) {
                    String seriesName = c.getSeries() != null ? c.getSeries().getSeriesName() : "—";
                    String chapterAction = switch (c.getStatus() != null ? c.getStatus().toLowerCase() : "") {
                        case "pass", "published" ->
                            "Đã duyệt chapter";
                        default ->
                            "Đã yêu cầu chỉnh sửa chapter"; // reject đưa status về unfinish
                    };
                    addActivity(activities, c.getReviewedAt(), "review-chapter", "fa-book",
                            chapterAction + " \"" + c.getChapterName() + "\" (series \"" + seriesName + "\")"
                            + (c.getTantouComment() != null && !c.getTantouComment().isBlank()
                            ? " — nhận xét: " + c.getTantouComment() : ""));
                }
            }
        }

        // 5b. Hành động không thể suy ra được từ trạng thái hiện tại — ghi trực
        // tiếp vào ActivityLog lúc xảy ra (mangaka: giao lại, nộp chap, tạo chapter,
        // tạo/sửa kịch bản, tạo/xóa trang; tantou: nhận xét trang). Vòng lặp bên
        // dưới không lọc theo role — ai làm gì thì tự khớp đúng UserID người đó.
        for (ActivityLog log : activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            addActivity(activities, log.getCreatedAt(), log.getType(), activityIcon(log.getType()),
                    log.getDescription());
        }

        // 5. Admin: xuất bản chapter
        if ("admin".equals(role)) {
            for (PublicDate pd : publicDateRepository.findAll()) {
                if (pd.getChapter() == null) {
                    continue;
                }
                String seriesName = pd.getChapter().getSeries() != null
                        ? pd.getChapter().getSeries().getSeriesName()
                        : "—";
                addActivity(activities, pd.getDatePublic(), "publish-chapter", "fa-upload",
                        "Đã xuất bản chapter \"" + pd.getChapter().getChapterName() + "\" (series " + seriesName + ")");
            }
        }

        // Sort theo timestamp DESC (chuỗi rỗng đẩy xuống cuối)
        activities.sort((a, b) -> {
            String ta = (String) a.get("timestamp");
            String tb = (String) b.get("timestamp");
            if (ta == null) {
                ta = "";
            }
            if (tb == null) {
                tb = "";
            }
            if (ta.isEmpty() && tb.isEmpty()) {
                return 0;
            }
            if (ta.isEmpty()) {
                return 1;
            }
            if (tb.isEmpty()) {
                return -1;
            }
            return tb.compareTo(ta);
        });

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", target.getId());
        userInfo.put("fullname", target.getFullname());
        userInfo.put("username", target.getUsername());
        userInfo.put("email", target.getEmail());
        userInfo.put("role", target.getRole());

        result.put("status", "success");
        result.put("user", userInfo);
        result.put("activities", activities);
        return result;
    }

    /**
     * Thêm 1 dòng hoạt động. Bỏ qua nếu chưa có mốc thời gian (nghĩa là hành
     * động đó chưa xảy ra, vd bài chưa nộp thì không có submittedAt).
     */
    private void addActivity(List<Map<String, Object>> activities, Object timestamp,
            String type, String icon, String description) {
        if (timestamp == null) {
            return;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("timestamp", timestamp.toString());
        a.put("type", type);
        a.put("icon", icon);
        a.put("description", description);
        activities.add(a);
    }

    /**
     * Icon hiển thị cho từng loại hoạt động ghi qua ActivityLogService.
     */
    private String activityIcon(String type) {
        if (type == null) {
            return "fa-clock";
        }
        return switch (type) {
            case "reassign-task" ->
                "fa-arrows-rotate";
            case "submit-chapter" ->
                "fa-paper-plane";
            case "comment-page" ->
                "fa-comment-dots";
            case "create-chapter" ->
                "fa-book-medical";
            case "create-script", "edit-script" ->
                "fa-pen-to-square";
            case "create-page" ->
                "fa-file-circle-plus";
            case "delete-page" ->
                "fa-trash";
            default ->
                "fa-clock";
        };
    }

    /**
     * Mô tả trang của 1 submission, vd: trang 3 (chapter "Chuong 1").
     */
    private String describePage(Submission s) {
        if (s.getPageId() == null) {
            return "task " + s.getId();
        }
        MangaPage pg = s.getPageId();
        String chapterName = pg.getChapter() != null ? pg.getChapter().getChapterName() : "—";
        return "trang " + (pg.getPageNumber() != null ? pg.getPageNumber() : "?")
                + " (chapter \"" + chapterName + "\")";
    }

    // ── Helper ────────────────────────────────────────────────────
    private List<Integer> getMonthsForQuarter(int quarter) {
        int start = (quarter - 1) * 3 + 1;
        return List.of(start, start + 1, start + 2);
    }

    private boolean isSet(Map<String, String> body, String key) {
        return body.containsKey(key) && body.get(key) != null && !body.get(key).isBlank();
    }

    /**
     * ID kế tiếp cho profile theo role, đúng định dạng đang dùng trong DB
     * (MGK/AST/EDT/BRD + số) — dựa trên ID lớn nhất hiện có, không dùng
     * count()+1 (count()+1 sinh trùng ID cũ sau khi 1 profile bị xóa, gây đè
     * dữ liệu của profile khác đang tồn tại).
     */
    private String nextProfileId(String role) {
        return switch (role.toLowerCase()) {
            case "mangaka" -> nextId("MGK", mangakaRepository.findTopByOrderByIdDesc().map(Mangaka::getId));
            case "assistant" -> nextId("AST", assistantRepository.findTopByOrderByIdDesc().map(Assistant::getId));
            case "tantou" -> nextId("EDT", tantoEditorRepository.findTopByOrderByIdDesc().map(TantoEditor::getId));
            case "board" -> nextId("BRD", boardRepository.findTopByOrderByIdDesc().map(Board::getId));
            default -> null;
        };
    }

    private String nextId(String prefix, Optional<String> lastId) {
        int lastNum = lastId.map(id -> Integer.parseInt(id.replaceAll("[^0-9]", ""))).orElse(0);
        return prefix + String.format("%03d", lastNum + 1);
    }

    // ════════════════════════════════════════════════════════════
// TÍNH NĂNG 3: IMPORT EXCEL
// ════════════════════════════════════════════════════════════
    @Operation(summary = "Import danh sách tài khoản hàng loạt từ file Excel")
    @PostMapping(value = "/import-excel", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(@RequestPart("file") MultipartFile file, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null || !"admin".equalsIgnoreCase(user.getRole())) {
            result.put("status", "error");
            result.put("message", "Không có quyền truy cập");
            return result;
        }
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".xlsx")) {
            result.put("status", "error");
            result.put("message", "file phải là .xlsx");
            return result;
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            // Lượt xem giờ nằm chung với like/dislike theo từng series-tháng-năm,
            // nên chỉ còn 1 sheet duy nhất: LikeDislike (SeriesID, Month, Year,
            // ViewCount, LikeNumber, DislikeNumber).
            Sheet likeSheet = workbook.getSheet("LikeDislike");

            if (likeSheet == null) {
                result.put("status", "error");
                result.put("message", "File phải có sheet 'LikeDislike'");
                return result;
            }

            List<String> errors = new ArrayList<>();

            // ============================================
            // BƯỚC 1: VALIDATE TOÀN BỘ FILE
            // ============================================
            for (Row row : likeSheet) {
                if (row.getRowNum() == 0) {
                    continue; // Skip header
                }
                String seriesId = getCellStringValue(row.getCell(0));
                String monthStr = getCellStringValue(row.getCell(1));
                String yearStr = getCellStringValue(row.getCell(2));
                String viewStr = getCellStringValue(row.getCell(3));
                String likeStr = getCellStringValue(row.getCell(4));
                String dislikeStr = getCellStringValue(row.getCell(5));

                if (seriesId.isEmpty() && monthStr.isEmpty() && yearStr.isEmpty()
                        && viewStr.isEmpty() && likeStr.isEmpty() && dislikeStr.isEmpty()) {
                    continue;
                }

                if (seriesId.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": SeriesID không được để trống");
                } else {
                    Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
                    if (seriesOpt.isEmpty()) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": SeriesID '" + seriesId
                                + "' không tồn tại");
                    }
                }

                if (monthStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(monthStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add(
                                    "Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month phải là số nguyên");
                        } else {
                            int month = (int) parsed;
                            if (month < 1 || month > 12) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month = " + month
                                        + " không hợp lệ (phải từ 1-12)");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Month phải là số nguyên");
                    }
                }

                if (yearStr.isEmpty()) {
                    errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year không được để trống");
                } else {
                    try {
                        double parsed = Double.parseDouble(yearStr.replace(",", ""));
                        if (parsed % 1 != 0) {
                            errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year phải là số nguyên");
                        } else {
                            int year = (int) parsed;
                            if (year <= 2000) {
                                errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year = " + year
                                        + " không hợp lệ (phải > 2000)");
                            }
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Sheet LikeDislike, dòng " + (row.getRowNum() + 1) + ": Year phải là số nguyên");
                    }
                }

                validateNonNegativeInt(viewStr, "ViewCount", row.getRowNum(), errors);
                validateNonNegativeInt(likeStr, "LikeNumber", row.getRowNum(), errors);
                validateNonNegativeInt(dislikeStr, "DislikeNumber", row.getRowNum(), errors);
            }

            if (!errors.isEmpty()) {
                result.put("status", "error");
                result.put("errors", errors);
                return result;
            }

            // ============================================
            // BƯỚC 2: LƯU VÀO DB NẾU VALIDATE PASS
            // ============================================
            int updatedLikeCount = 0;
            for (Row row : likeSheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String seriesId = getCellStringValue(row.getCell(0));
                String monthStr = getCellStringValue(row.getCell(1));
                String yearStr = getCellStringValue(row.getCell(2));
                String viewStr = getCellStringValue(row.getCell(3));
                String likeStr = getCellStringValue(row.getCell(4));
                String dislikeStr = getCellStringValue(row.getCell(5));
                if (seriesId.isEmpty() && monthStr.isEmpty() && yearStr.isEmpty()
                        && viewStr.isEmpty() && likeStr.isEmpty() && dislikeStr.isEmpty()) {
                    continue;
                }

                int month = (int) Double.parseDouble(monthStr.replace(",", ""));
                int year = (int) Double.parseDouble(yearStr.replace(",", ""));
                int views = (int) Double.parseDouble(viewStr.replace(",", ""));
                int likes = (int) Double.parseDouble(likeStr.replace(",", ""));
                int dislikes = (int) Double.parseDouble(dislikeStr.replace(",", ""));

                Optional<LikeResult> lrOpt = likeResultRepository.findBySeriesIdAndMonthAndYear(seriesId, month, year);
                if (lrOpt.isPresent()) {
                    LikeResult lr = lrOpt.get();
                    lr.setViewCount(views);
                    lr.setLikeNumber(likes);
                    lr.setDislikeNumber(dislikes);
                    likeResultRepository.save(lr);
                } else {
                    LikeResult lr = new LikeResult();
                    lr.setId(UUID.randomUUID().toString().substring(0, 7));
                    Series series = seriesRepository.findById(seriesId).get();
                    lr.setSeries(series);
                    lr.setViewCount(views);
                    lr.setLikeNumber(likes);
                    lr.setDislikeNumber(dislikes);
                    lr.setMonth(month);
                    lr.setYear(year);
                    likeResultRepository.save(lr);
                }
                updatedLikeCount++;
            }

            result.put("status", "success");
            result.put("message", "Import thành công " + updatedLikeCount + " dòng view/like/dislike");
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", "Lỗi xử lý Excel: " + e.getMessage());
        }

        return result;
    }

    /**
     * Validate 1 ô phải là số nguyên >= 0, thêm lỗi vào danh sách nếu sai.
     */
    private void validateNonNegativeInt(String value, String fieldName, int rowNum, List<String> errors) {
        if (value.isEmpty()) {
            errors.add("Sheet LikeDislike, dòng " + (rowNum + 1) + ": " + fieldName + " không được để trống");
            return;
        }
        try {
            double parsed = Double.parseDouble(value.replace(",", ""));
            if (parsed % 1 != 0) {
                errors.add("Sheet LikeDislike, dòng " + (rowNum + 1) + ": " + fieldName + " phải là số nguyên");
            } else if ((int) parsed < 0) {
                errors.add("Sheet LikeDislike, dòng " + (rowNum + 1) + ": " + fieldName + " không được âm");
            }
        } catch (NumberFormatException e) {
            errors.add("Sheet LikeDislike, dòng " + (rowNum + 1) + ": " + fieldName + " phải là số nguyên");
        }
    }

    private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return "";
        }
        org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private int getCellIntValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return 0;
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            try {
                return (int) cell.getNumericCellValue();
            } catch (Exception e) {
                return 0;
            }
        } else {
            org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            String val = formatter.formatCellValue(cell).trim();
            if (val.isEmpty()) {
                return 0;
            }
            try {
                return (int) Double.parseDouble(val); // Handle cases like "1.0"
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    @Operation(summary = "Tải file Excel mẫu để import tài khoản")
    @GetMapping("/download-template")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet likeSheet = workbook.createSheet("LikeDislike");
            Row ldHeaderRow = likeSheet.createRow(0);
            ldHeaderRow.createCell(0).setCellValue("SeriesID");
            ldHeaderRow.createCell(1).setCellValue("Month");
            ldHeaderRow.createCell(2).setCellValue("Year");
            ldHeaderRow.createCell(3).setCellValue("ViewCount");
            ldHeaderRow.createCell(4).setCellValue("LikeNumber");
            ldHeaderRow.createCell(5).setCellValue("DislikeNumber");

            Row ldDataRow = likeSheet.createRow(1);
            ldDataRow.createCell(0).setCellValue("SER001");
            ldDataRow.createCell(1).setCellValue(1);
            ldDataRow.createCell(2).setCellValue(2026);
            ldDataRow.createCell(3).setCellValue(15000);
            ldDataRow.createCell(4).setCellValue(1000);
            ldDataRow.createCell(5).setCellValue(200);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=template_import_manga.xlsx");
            return new org.springframework.http.ResponseEntity<>(out.toByteArray(), headers,
                    org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            return new org.springframework.http.ResponseEntity<>(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
