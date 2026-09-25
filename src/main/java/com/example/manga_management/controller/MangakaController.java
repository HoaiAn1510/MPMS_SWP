package com.example.manga_management.controller;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.VoteSessionRepository;
import com.example.manga_management.service.ActivityLogService;
import com.example.manga_management.service.BookJacketStorageService;
import com.example.manga_management.service.DataAccessService;
import com.example.manga_management.service.FileStorageService;
import com.example.manga_management.service.ProposalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@Tag(name = "Mangaka Controller", description = "Endpoints for Mangaka operations, including project submission, series management, and chapter creation.")
@Controller
@RequestMapping("/manga/mangaka")
public class MangakaController {

    private final SubmissionRepository submissionRepository;
    private final ProposalRepository proposalRepository;
    private final MangakaRepository mangakaRepository;
    private final SeriesRepository seriesRepository;
    private final com.example.manga_management.repository.ChapterRepository chapterRepository;
    private final MangaPageRepository mangaPageRepository;
    private final AssistantRepository assistantRepository;
    private final ProposalService proposalService;
    private NotificationController notificationController;
    private final ActivityLogService activityLogService;
    private final VoteSessionRepository voteSessionRepository;
    private final BookJacketStorageService bookJacketStorageService;
    private final FileStorageService fileStorageService;
    private final DataAccessService dataAccessService;

    public MangakaController(ProposalRepository proposalRepository, MangakaRepository mangakaRepository,
            SeriesRepository seriesRepository,
            com.example.manga_management.repository.ChapterRepository chapterRepository,
            MangaPageRepository mangaPageRepository, SubmissionRepository submissionRepository,
            NotificationController notificationController, AssistantRepository assistantRepository,
            ProposalService proposalService, ActivityLogService activityLogService,
            VoteSessionRepository voteSessionRepository,
            BookJacketStorageService bookJacketStorageService,
            FileStorageService fileStorageService, DataAccessService dataAccessService) {
        this.fileStorageService = fileStorageService;
        this.dataAccessService = dataAccessService;
        this.proposalRepository = proposalRepository;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.submissionRepository = submissionRepository;
        this.notificationController = notificationController;
        this.proposalService = proposalService;
        this.activityLogService = activityLogService;
        this.voteSessionRepository = voteSessionRepository;
        this.bookJacketStorageService = bookJacketStorageService;
    }

    /** Series này có thuộc đúng Mangaka đang đăng nhập không. */
    private boolean isOwnSeries(Series series, User user) {
        return series != null && user != null && series.getProposal() != null
                && series.getProposal().getMangaka() != null
                && series.getProposal().getMangaka().getUser() != null
                && series.getProposal().getMangaka().getUser().getId().equals(user.getId());
    }

    /** Submission này thuộc series của Mangaka đang đăng nhập không. */
    private boolean isOwnSubmission(Submission submission, User user) {
        return submission != null && submission.getPageId() != null
                && submission.getPageId().getChapter() != null
                && isOwnSeries(submission.getPageId().getChapter().getSeries(), user);
    }

    @GetMapping({ "" })
    public String mangakaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("currentUserId", user.getId());
        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        model.addAttribute("mangaka", mangaka);
        if (mangaka != null) {
            model.addAttribute("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
            model.addAttribute("approvedList",
                    proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                            mangaka.getId()));
            model.addAttribute("tantouApprovedList",
                    proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check"),
                            mangaka.getId()));
            model.addAttribute("boardApprovedList",
                    proposalRepository.findByStatusAndMangaka_Id("passed", mangaka.getId()));
            model.addAttribute("revisionList",
                    proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
            model.addAttribute("lockedList",
                    proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));
            addMonthlyEarningsToModel(mangaka, model);
        }
        return "mangaka";
    }

    /**
     * Thống kê thu nhập trong tháng của mangaka, hiển thị ở tab "Dự án":
     * - Số chapter được tantou duyệt trong tháng ("pass"/"published", theo ReviewedAt).
     * - Lương tháng này = số chapter đó x salaryPerChapter.
     * - Thưởng tháng này = tổng rewardBonusAmount đã "chốt" từ các phiên vote thưởng
     *   ĐẠT trong tháng (đã tính 1 lần lúc phiên đóng, không tính lại ở đây).
     * - Tổng lương tháng này = lương + thưởng.
     */
    private void addMonthlyEarningsToModel(Mangaka mangaka, Model model) {
        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = LocalDateTime.of(now.getYear(), now.getMonthValue(), 1, 0, 0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        LocalDate monthStartDate = monthStart.toLocalDate();
        LocalDate monthEndDate = monthStartDate.plusMonths(1).minusDays(1);

        List<Chapter> reviewedThisMonth = chapterRepository
                .findBySeries_Proposal_Mangaka_IdAndReviewedAtBetween(mangaka.getId(), monthStart, monthEnd);
        long chaptersApprovedThisMonth = reviewedThisMonth.stream()
                .filter(c -> "pass".equals(c.getStatus()) || "published".equals(c.getStatus()))
                .count();

        int salaryThisMonth = (int) (chaptersApprovedThisMonth * mangaka.getSalaryPerChapter());

        List<VoteSession> rewardSessionsThisMonth = voteSessionRepository
                .findBySeries_Proposal_Mangaka_IdAndVoteTypeAndResultPassedTrueAndClosedAtBetween(
                        mangaka.getId(), "reward", monthStartDate, monthEndDate);
        int bonusThisMonth = rewardSessionsThisMonth.stream()
                .mapToInt(vs -> vs.getRewardBonusAmount() != null ? vs.getRewardBonusAmount() : 0)
                .sum();

        model.addAttribute("salaryPerChapter", mangaka.getSalaryPerChapter());
        model.addAttribute("chaptersApprovedThisMonth", chaptersApprovedThisMonth);
        model.addAttribute("mangakaSalaryThisMonth", salaryThisMonth);
        model.addAttribute("mangakaBonusThisMonth", bonusThisMonth);
        model.addAttribute("mangakaTotalSalaryThisMonth", salaryThisMonth + bonusThisMonth);
        model.addAttribute("currentMonthLabel", "Tháng " + now.getMonthValue());
    }

    // ===================== SWAGGER / JSON ENDPOINTS =====================
    @Operation(summary = "[SWAGGER] Xem tất cả proposals của một Mangaka")
    @GetMapping("/my-projects/data")
    @ResponseBody
    public Map<String, Object> myProjectsData(@RequestParam String mangakaId, Model model, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Mangaka với ID: " + mangakaId);
            return result;
        }
        if (!dataAccessService.isMangakaUser(mangaka, (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem dữ liệu này!");
            return result;
        }

        model.addAttribute("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
        model.addAttribute("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                        mangaka.getId()));
        model.addAttribute("tantouApprovedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check"),
                        mangaka.getId()));
        model.addAttribute("boardApprovedList",
                proposalRepository.findByStatusAndMangaka_Id("passed", mangaka.getId()));
        model.addAttribute("revisionList",
                proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
        model.addAttribute("lockedList",
                proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));

        result.put("status", "success");
        result.put("allProposals",
                proposalRepository.findByMangaka_Id(mangaka.getId()));
        result.put("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                        mangaka.getId()));
        result.put("tantouApprovedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check"),
                        mangaka.getId()));
        result.put("boardApprovedList",
                proposalRepository.findByStatusAndMangaka_Id("passed", mangaka.getId()));
        result.put("revisionList",
                proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
        result.put("lockedList",
                proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));
        return result;
    }

    @Operation(summary = "[SWAGGER] Nộp bản thảo mới")
    /**
     * Tên series/bản thảo phải duy nhất trên toàn hệ thống: không trùng series đã
     * có và không trùng đề xuất còn hiệu lực. Bỏ qua đề xuất đã bị từ chối
     * ("locked" — tên được dùng lại) và bỏ qua chính đề xuất đang sửa (excludeId).
     * So sánh không phân biệt hoa thường.
     */
    private boolean isSeriesNameTaken(String name, String excludeProposalId) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String trimmed = name.trim();
        if (seriesRepository.existsBySeriesNameIgnoreCase(trimmed)) {
            return true;
        }
        for (Proposal p : proposalRepository.findBySeriesNameIgnoreCase(trimmed)) {
            if (excludeProposalId != null && excludeProposalId.equals(p.getId())) {
                continue;
            }
            if ("locked".equals(p.getStatus())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Số chương dự kiến là phần bắt buộc của đề xuất: Tantou và Hội đồng phải nhìn
     * thấy con số này trước khi bỏ phiếu, vì sau khi duyệt nó trở thành trần cứng
     * cho series. Trả về message lỗi, hoặc null nếu hợp lệ.
     */
    private String validatePlannedChapterCount(Integer plannedChapterCount) {
        if (plannedChapterCount == null) {
            return "Vui lòng nhập số chương dự kiến của bộ truyện!";
        }
        if (plannedChapterCount < Proposal.MIN_PLANNED_CHAPTERS
                || plannedChapterCount > Proposal.MAX_PLANNED_CHAPTERS) {
            return "Số chương dự kiến phải nằm trong khoảng "
                    + Proposal.MIN_PLANNED_CHAPTERS + " - " + Proposal.MAX_PLANNED_CHAPTERS + "!";
        }
        return null;
    }

    @PostMapping(value = "/submit-proposal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> handleSubmitting(Model model,
            @RequestParam String txtSeriesName,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer plannedChapterCount,
            @Parameter(description = "Manuscript file") @RequestPart MultipartFile fileManuscript,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();
        // Đề xuất luôn được gán cho đúng Mangaka đang đăng nhập — không nhận
        // mangakaId từ client để tránh mạo danh (nộp hộ/gán cho mangaka khác).
        Mangaka currentMangaka = null;
        User user = (User) session.getAttribute("user");
        if (user != null) {
            currentMangaka = mangakaRepository.findByUser(user).orElse(null);
        }

        if (currentMangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy Mangaka!");
            return result;
        }
        if (fileManuscript.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bản thảo!");
            return result;
        }

        String fileName = fileManuscript.getOriginalFilename();

        if (fileName == null
                || !fileName.toLowerCase().endsWith(".pdf")) {

            result.put("status", "error");
            result.put("message", "Chỉ được phép tải lên file PDF!");
            return result;
        }

        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên series!");
            return result;
        }
        if (isSeriesNameTaken(txtSeriesName, null)) {
            result.put("status", "error");
            result.put("message", "Tên series \"" + txtSeriesName.trim()
                    + "\" đã tồn tại, vui lòng chọn tên khác!");
            return result;
        }
        String chapterCountError = validatePlannedChapterCount(plannedChapterCount);
        if (chapterCountError != null) {
            result.put("status", "error");
            result.put("message", chapterCountError);
            return result;
        }

        try {
            // Dựa trên ID lớn nhất hiện có (không dùng count()+1) — count()+1 sẽ
            // sinh trùng ID cũ sau khi có đề xuất bị xoá, gây đè dữ liệu.
            String lastProposalId = proposalRepository.findTopByOrderByIdDesc()
                    .map(Proposal::getId).orElse("PPS000");
            int nextNum = Integer.parseInt(lastProposalId.replaceAll("[^0-9]", "")) + 1;
            String nextId = String.format("PPS%03d", nextNum);

            // Tên file do server sinh; nội dung phải thật sự là PDF.
            String savedPath = fileStorageService.saveUpload(FileStorageService.DIR_PROPOSAL, nextId,
                    fileManuscript, FileStorageService.PDF_EXTENSIONS);

            Proposal proposal = new Proposal();
            proposal.setId(nextId);
            proposal.setMangaka(currentMangaka);
            proposal.setSeriesName(txtSeriesName.trim());
            proposal.setGenre(genre != null ? genre.trim() : null);
            proposal.setPlannedChapterCount(plannedChapterCount);
            proposal.setFilePath(savedPath);
            proposal.setStatus("new");
            proposal.setSubmittedAt(java.time.LocalDateTime.now());
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Có đề xuất mới từ Mangaka đang chờ duyệt: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", nextId);
            result.put("message", "Đã nộp bản thảo thành công!");

        } catch (IllegalArgumentException e) {
            result.put("status", "error");
            result.put("message", "File PDF không hợp lệ: " + e.getMessage());
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        model.addAttribute("activeTab", "tab-proposal");
        return result;
    }

    @PostMapping(value = "/resubmit-proposal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> resubmitProposal(@RequestParam("proposalId") String proposalId,
            @RequestParam("txtSeriesName") String txtSeriesName,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer plannedChapterCount,
            @RequestParam("fileManuscript") MultipartFile fileManuscript) {

        Map<String, String> result = new HashMap<>();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        if ("locked".equals(proposal.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này không thể nộp lại.");
            return result;
        }

        if (!"revision".equals(proposal.getStatus())) {
            result.put("status", "error");
            result.put("message", "Đề xuất này hiện không ở trạng thái chờ sửa!");
            return result;
        }

        if (fileManuscript.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bản thảo mới!");
            return result;
        }
        if (txtSeriesName == null || txtSeriesName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên series!");
            return result;
        }
        if (isSeriesNameTaken(txtSeriesName, proposalId)) {
            result.put("status", "error");
            result.put("message", "Tên series \"" + txtSeriesName.trim()
                    + "\" đã tồn tại, vui lòng chọn tên khác!");
            return result;
        }
        // Nộp lại cũng phải khai lại số chương — tác giả có thể điều chỉnh phạm vi
        // theo góp ý của Tantou, và bản Hội đồng bỏ phiếu phải là bản mới nhất.
        String chapterCountError = validatePlannedChapterCount(plannedChapterCount);
        if (chapterCountError != null) {
            result.put("status", "error");
            result.put("message", chapterCountError);
            return result;
        }

        try {
            // Lưu file mới trước; chỉ xóa file cũ khi file mới đã ghi thành công
            // (file mới không hợp lệ thì bản cũ vẫn còn nguyên).
            String oldPath = proposal.getFilePath();
            String savedPath = fileStorageService.saveUpload(FileStorageService.DIR_PROPOSAL, proposalId,
                    fileManuscript, FileStorageService.PDF_EXTENSIONS);
            if (oldPath != null && !oldPath.equals(savedPath)) {
                fileStorageService.delete(oldPath);
            }

            proposal.setSeriesName(txtSeriesName.trim());
            proposal.setGenre(genre != null ? genre.trim() : null);
            proposal.setPlannedChapterCount(plannedChapterCount);
            proposal.setFilePath(savedPath);
            proposal.setStatus("new");
            proposal.setComment(null);
            proposal.setEditorScore(null);
            proposal.setRevisionDeadline(null);
            proposal.setSubmittedAt(java.time.LocalDateTime.now());
            proposal.setReviewedAt(null);
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Mangaka đã nộp lại bản thảo: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("proposalId", proposalId);
            result.put("message", "Đã nộp lại bản thảo thành công!");

        } catch (IllegalArgumentException e) {
            result.put("status", "error");
            result.put("message", "File PDF không hợp lệ: " + e.getMessage());
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Xem chi tiết đề xuất: comment/điểm/deadline của Tantou + comment của hội đồng")
    @GetMapping("/proposal-detail")
    @ResponseBody
    public Map<String, Object> proposalDetail(@RequestParam String proposalId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Proposal p = proposalRepository.findById(proposalId).orElse(null);
        if (p == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }
        User requester = (User) session.getAttribute("user");
        if (!dataAccessService.isMangakaUser(p.getMangaka(), requester)
                && !dataAccessService.isTantouOfMangaka(p.getMangaka(), requester)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem đề xuất này!");
            return result;
        }

        result.put("status", "success");
        result.put("proposalId", p.getId());
        result.put("seriesName", p.getSeriesName());
        result.put("plannedChapterCount", p.getPlannedChapterCount());
        result.put("proposalStatus", p.getStatus());
        result.put("filePath", p.getFilePath()); // file bản thảo mangaka đã nộp
        result.put("editorComment", p.getComment());
        result.put("editorScore", p.getEditorScore());
        result.put("revisionDeadline", p.getRevisionDeadline()); // FE tự so sánh với thời gian hiện tại nếu muốn hiển
        // thị "còn X ngày"
        result.put("submittedAt", p.getSubmittedAt() != null ? p.getSubmittedAt() : p.getCreatedAt());
        result.put("reviewedAt", p.getReviewedAt());
        result.put("boardSubmittedAt", p.getBoardSubmittedAt());
        result.put("boardReviewedAt", p.getBoardReviewedAt());

        List<Map<String, Object>> boardComments = proposalService
                .getCommentsForProposal(p.getId())
                .stream()
                .map(c -> Map.<String, Object>of(
                        "action", c.getAction(),
                        "content", c.getContent() == null ? "" : c.getContent(),
                        "createdAt", c.getCreatedAt()))
                .toList();
        result.put("boardComments", boardComments);

        return result;
    }

    @Operation(summary = "[SWAGGER] Khởi động series từ proposal đã được duyệt")
    @PostMapping(value = "/start-series", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, String> startSeries(@RequestParam String proposalId, @RequestParam String txtSeriesName,
            @RequestParam String txtDescription, @RequestPart MultipartFile fileBookJacket,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();

        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy đề xuất: " + proposalId);
            return result;
        }

        User requester = (User) session.getAttribute("user");
        if (requester == null || proposal.getMangaka() == null || proposal.getMangaka().getUser() == null
                || !proposal.getMangaka().getUser().getId().equals(requester.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền khởi động series từ đề xuất này!");
            return result;
        }

        if (fileBookJacket.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file bìa sách!");
            return result;
        }

        String storedBookJacket = null;
        try {
            // Dựa trên ID lớn nhất hiện có, không dùng count()+1 (tránh trùng ID sau
            // khi có series bị xóa).
            String lastSeriesId = seriesRepository.findTopByOrderByIdDesc()
                    .map(Series::getId).orElse("SER000");
            int nextSeriesNum = Integer.parseInt(lastSeriesId.replaceAll("[^0-9]", "")) + 1;
            String seriesId = String.format("SER%03d", nextSeriesNum);
            storedBookJacket = bookJacketStorageService.store(fileBookJacket, seriesId);

            Series series = new Series();
            series.setId(seriesId);
            series.setProposal(proposal);
            series.setSeriesName(txtSeriesName);
            series.setDescription(txtDescription);
            series.setGenre(proposal.getGenre());
            // Trần số chương lấy nguyên từ đề xuất Hội đồng đã thông qua — không
            // nhận từ client, để tác giả không tự nới rộng phạm vi sau khi duyệt.
            series.setPlannedChapterCount(proposal.getPlannedChapterCount());
            series.setBookJacket(storedBookJacket);
            series.setStartDate(LocalDate.now());
            series.setStatus("unfinish");
            seriesRepository.save(series);

            proposal.setStatus("started");
            proposalRepository.save(proposal);

            notificationController.send("tantou", null, "Mangaka đã khởi động dự án mới: " + txtSeriesName,
                    "/manga/editor");

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Khởi động tác phẩm thành công!");

        } catch (IllegalArgumentException e) {
            bookJacketStorageService.deleteIfManaged(storedBookJacket);
            result.put("status", "error");
            result.put("message", e.getMessage());
        } catch (IOException e) {
            bookJacketStorageService.deleteIfManaged(storedBookJacket);
            result.put("status", "error");
            result.put("message", "Lỗi lưu ảnh bìa: " + e.getMessage());
        } catch (RuntimeException e) {
            // A repository or notification failure can happen after the series has
            // already been saved. Keep the uploaded file so a persisted series never
            // points to a cover that this error handler just deleted.
            result.put("status", "error");
            result.put("message", "Không thể lưu series. Vui lòng thử lại!");
        }
        return result;
    }

    private LocalDateTime resolveNextSaturday(Series series) {
        Optional<Chapter> latest = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
        if (latest.isPresent() && latest.get().getDeadline() != null) {
            return latest.get().getDeadline().plusWeeks(1);
        }
        LocalDate nextSaturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        return LocalDateTime.of(nextSaturday, LocalTime.of(23, 59));
    }

    @Operation(summary = "[SWAGGER] Lấy dữ liệu dashboard của Mangaka")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> getMangakaData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy mangaka");
            return result;
        }
        result.put("status", "success");
        result.put("mangaka", mangaka);
        result.put("mySeriesList", seriesRepository.findByProposal_Mangaka_Id(mangaka.getId()));
        result.put("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
        result.put("approvedList",
                proposalRepository.findByStatusInAndMangaka_Id(List.of("checked", "pass"), mangaka.getId()));
        result.put("rejectedList", proposalRepository.findByStatusAndMangaka_Id("unfinish", mangaka.getId()));
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách chapter của một series")
    @GetMapping("/myseries/{seriesId}/data")
    @ResponseBody
    public Map<String, Object> getSeriesData(@PathVariable String seriesId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }
        if (!dataAccessService.isSeriesStaff(series, (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem series này!");
            return result;
        }
        result.put("status", "success");
        result.put("series", series);
        List<Chapter> chapters = chapterRepository.findBySeries(series);
        Map<String, Map<String, Object>> chapterCoverMap = new HashMap<>();
        for (Chapter chapter : chapters) {
            mangaPageRepository.findFirstByChapterAndPageTypeOrderByPageNumberAsc(chapter, "cover")
                    .ifPresent(coverPage -> {
                        Map<String, Object> cover = new HashMap<>();
                        cover.put("pageId", coverPage.getId());
                        cover.put("filePath", coverPage.getFilePath());
                        chapterCoverMap.put(chapter.getId(), cover);
                    });
        }
        result.put("chapters", chapters);
        result.put("chapterCoverMap", chapterCoverMap);
        return result;
    }

    @Operation(summary = "[SWAGGER] Mangaka đánh dấu series đã hoàn thành")
    @PostMapping("/myseries/{seriesId}/complete")
    @ResponseBody
    public Map<String, Object> completeSeries(@PathVariable String seriesId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        // Chỉ chính chủ mangaka của series mới được đánh dấu hoàn thành.
        if (series.getProposal() == null || series.getProposal().getMangaka() == null
                || series.getProposal().getMangaka().getUser() == null
                || !series.getProposal().getMangaka().getUser().getId().equals(user.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền đánh dấu hoàn thành series này!");
            return result;
        }

        if ("completed".equals(series.getStatus())) {
            result.put("status", "error");
            result.put("message", "Series này đã hoàn thành rồi!");
            return result;
        }
        // Chỉ hoàn thành được series đang phát hành (đã xuất bản ít nhất 1 chapter).
        if (!"published".equals(series.getStatus())) {
            result.put("status", "error");
            result.put("message",
                    "Chỉ có thể hoàn thành series đã xuất bản ít nhất 1 chapter (đang phát hành)!");
            return result;
        }
        // Đang có phiên vote mở (dừng/khen thưởng) thì không được chốt hoàn thành.
        if (voteSessionRepository.existsBySeriesIdAndStatus(seriesId, "active")) {
            result.put("status", "error");
            result.put("message", "Series đang có phiên vote mở, không thể đánh dấu hoàn thành lúc này!");
            return result;
        }

        // Không được hoàn thành khi còn chapter đang dở (chưa xuất bản). Chỉ chấp
        // nhận chapter đã published (hoặc đã dừng/hủy) — mọi trạng thái khác là
        // "đang treo" và phải xử lý xong trước khi chốt hoàn thành series.
        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
        long pending = chapters.stream()
                .filter(c -> !"published".equals(c.getStatus())
                        && !"stopped".equals(c.getStatus())
                        && !"cancelled".equals(c.getStatus()))
                .count();
        if (pending > 0) {
            result.put("status", "error");
            result.put("message", "Còn " + pending
                    + " chapter chưa xuất bản. Vui lòng hoàn tất/xuất bản hết chapter trước khi đánh dấu hoàn thành series!");
            return result;
        }

        series.setStatus("completed");
        seriesRepository.save(series);

        // Báo cho tantou phụ trách và toàn bộ hội đồng biết series đã hoàn thành.
        String content = "🏁 Series '" + series.getSeriesName() + "' đã được đánh dấu HOÀN THÀNH.";
        var mangaka = series.getProposal().getMangaka();
        if (mangaka.getEditor() != null && mangaka.getEditor().getUser() != null) {
            notificationController.send(null, mangaka.getEditor().getUser().getId(), content, "/manga/tantou");
        }
        notificationController.send("board", null, content, "/manga/editor");

        result.put("status", "success");
        result.put("message", "Đã đánh dấu series '" + series.getSeriesName() + "' hoàn thành!");
        return result;
    }

    @Operation(summary = "[SWAGGER] Tạo chapter mới")
    @PostMapping("/myseries/{seriesId}/createchapter/data")
    @ResponseBody
    public Map<String, Object> createChapterData(@PathVariable String seriesId,
            @RequestParam String txtChapterName, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }
        User requester = (User) session.getAttribute("user");
        if (!isOwnSeries(series, requester)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên series này!");
            return result;
        }
        if (series.isLocked()) {
            result.put("status", "error");
            result.put("message", series.getLockMessage());
            return result;
        }
        if (txtChapterName == null || txtChapterName.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập tên chapter!");
            return result;
        }
        String chapterNameTrimmed = txtChapterName.trim();
        // Chặn trùng tên chapter trong CÙNG series (không phân biệt hoa thường).
        boolean dupChapterName = chapterRepository.findBySeries(series).stream()
                .anyMatch(c -> c.getChapterName() != null
                        && c.getChapterName().trim().equalsIgnoreCase(chapterNameTrimmed));
        if (dupChapterName) {
            result.put("status", "error");
            result.put("message", "Series này đã có chapter tên \"" + chapterNameTrimmed
                    + "\", vui lòng đặt tên khác!");
            return result;
        }
        // Trần số chương theo đúng đề xuất Hội đồng đã duyệt. Series cũ (tạo trước
        // khi có ràng buộc này) có plannedChapterCount = null nên không bị chặn.
        Integer planned = series.getPlannedChapterCount();
        if (planned != null) {
            long existing = chapterRepository.findBySeries(series).size();
            if (existing >= planned) {
                result.put("status", "error");
                result.put("message", "Series đã đạt số chương dự kiến trong đề xuất đã duyệt ("
                        + existing + "/" + planned + " chương). Không thể tạo thêm chapter mới — "
                        + "hãy đánh dấu series hoàn thành, hoặc nộp đề xuất mới nếu muốn kéo dài.");
                return result;
            }
        }
        try {
            Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();
            int maxId = 0;
            if (lastChapter.isPresent()) {
                maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
            }
            Optional<Chapter> lastChapterNumber = chapterRepository.findTopBySeriesOrderByChapterNumberDesc(series);
            int nextNumber = lastChapterNumber.map(Chapter::getChapterNumber).orElse(0) + 1;
            Chapter chapter = new Chapter();
            chapter.setId("CPT" + String.format("%04d", maxId + 1));
            chapter.setSeries(series);
            chapter.setChapterName(chapterNameTrimmed);
            chapter.setChapterNumber(nextNumber);
            chapter.setDeadline(resolveNextSaturday(series));
            chapter.setStatus("unfinish");
            chapterRepository.save(chapter);

            User user = (User) session.getAttribute("user");
            if (user != null) {
                activityLogService.log(user.getId(), "create-chapter",
                        "Đã tạo chapter \"" + txtChapterName + "\" (Chapter " + nextNumber
                                + ") trong series \"" + series.getSeriesName() + "\"");
            }

            result.put("status", "success");
            result.put("chapterId", chapter.getId());
            result.put("chapterNumber", nextNumber);
            result.put("message", "Tạo chapter thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách trang của một chapter")
    @GetMapping("/myseries/{sid}/{cid}/data")
    @ResponseBody
    public Map<String, Object> getChapterData(@PathVariable String sid, @PathVariable String cid,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Chapter chapter = chapterRepository.findById(cid).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + cid);
            return result;
        }
        if (!dataAccessService.isSeriesStaff(chapter.getSeries(), (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem chapter này!");
            return result;
        }
        try {
            List<MangaPage> pages = mangaPageRepository.findByChapterIdOrderByPageNumberAsc(cid);
            Map<String, Submission> submissionMap = new HashMap<>();
            for (MangaPage page : pages) {
                submissionRepository.findTopByPageIdIdOrderByIdDesc(page.getId())
                        .ifPresent(sub -> submissionMap.put(page.getId(), sub));
            }
            Mangaka mangaka = chapter.getSeries().getProposal().getMangaka();
            result.put("status", "success");
            result.put("chapter", chapter);
            result.put("pages", pages);
            result.put("submissionMap", submissionMap);
            result.put("mangakaId", mangaka.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Sửa ảnh bìa sách cho series đã có")
    @PostMapping(value = "/myseries/{seriesId}/edit-jacket", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> editSeriesJacket(@PathVariable String seriesId,
            @RequestPart MultipartFile fileBookJacket,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        // Chỉ mangaka sở hữu series mới được sửa ảnh bìa
        Mangaka owner = series.getProposal() != null ? series.getProposal().getMangaka() : null;
        if (owner == null || owner.getUser() == null || !owner.getUser().getId().equals(user.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền sửa ảnh bìa series này!");
            return result;
        }

        if (fileBookJacket.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn file ảnh bìa mới!");
            return result;
        }

        String oldBookJacket = series.getBookJacket();
        String newBookJacket = null;
        try {
            // Store a versioned file first. The old cover stays valid until both the
            // upload and database update have succeeded.
            newBookJacket = bookJacketStorageService.store(fileBookJacket, seriesId);
            series.setBookJacket(newBookJacket);
            seriesRepository.save(series);
            bookJacketStorageService.deleteIfManaged(oldBookJacket);

            result.put("status", "success");
            result.put("message", "Đã cập nhật ảnh bìa!");
            result.put("bookJacket", series.getBookJacket());
        } catch (IllegalArgumentException e) {
            series.setBookJacket(oldBookJacket);
            bookJacketStorageService.deleteIfManaged(newBookJacket);
            result.put("status", "error");
            result.put("message", e.getMessage());
        } catch (IOException e) {
            series.setBookJacket(oldBookJacket);
            bookJacketStorageService.deleteIfManaged(newBookJacket);
            result.put("status", "error");
            result.put("message", "Lỗi lưu ảnh bìa: " + e.getMessage());
        } catch (RuntimeException e) {
            series.setBookJacket(oldBookJacket);
            bookJacketStorageService.deleteIfManaged(newBookJacket);
            result.put("status", "error");
            result.put("message", "Không thể cập nhật ảnh bìa. Vui lòng thử lại!");
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Thêm trang mới vào chapter")
    @PostMapping("/myseries/{seriesId}/{chapterId}/addpage/data")
    @ResponseBody
    public Map<String, Object> addPageData(@PathVariable String seriesId,
            @PathVariable String chapterId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy chapter: " + chapterId);
            return result;
        }

        User requester = (User) session.getAttribute("user");
        if (!isOwnSeries(chapter.getSeries(), requester)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên chapter này!");
            return result;
        }

        if (chapter.getSeries() != null && chapter.getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", chapter.getSeries().getLockMessage());
            return result;
        }

        if (!"unfinish".equals(chapter.getStatus())) {
            result.put("status", "error");
            result.put("message", "Chỉ có thể thêm trang khi chapter đang ở trạng thái unfinish");
            return result;
        }

        // ✅ Chapter phải có kịch bản thì mới được tạo trang mới
        if (chapter.getScript() == null || chapter.getScript().trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Chapter chưa có kịch bản. Hãy tạo kịch bản trước khi tạo trang mới!");
            return result;
        }

        String pageType = body != null ? body.get("pageType") : null;
        String pageScript = body != null ? body.get("script") : null;

        List<String> allowedTypes = List.of("cover", "action", "rest", "info", "end");
        if (pageType == null || !allowedTypes.contains(pageType)) {
            result.put("status", "error");
            result.put("message", "Vui lòng chọn thể loại trang!");
            return result;
        }
        if (pageScript == null || pageScript.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập kịch bản trang!");
            return result;
        }
        if (pageScript.trim().length() > 1000) {
            result.put("status", "error");
            result.put("message", "Kịch bản trang tối đa 1000 chữ!");
            return result;
        }

        try {
            // ✅ Sinh ID dựa trên ID lớn nhất hiện có, không dùng count()
            Optional<MangaPage> lastPage = mangaPageRepository.findTopByOrderByIdDesc();
            int maxId = 0;
            if (lastPage.isPresent()) {
                maxId = Integer.parseInt(lastPage.get().getId().substring(2)); // bỏ tiền tố "PG"
            }
            String pageId = String.format("PG%05d", maxId + 1);

            List<MangaPage> existing = mangaPageRepository.findByChapter(chapter);
            if ("cover".equals(pageType) && existing.stream()
                    .anyMatch(page -> "cover".equalsIgnoreCase(page.getPageType()))) {
                result.put("status", "error");
                result.put("message", "Chapter này đã có trang bìa. Hãy xóa trang bìa hiện tại trước khi tạo trang bìa mới!");
                return result;
            }
            // Dựa trên PageNumber lớn nhất hiện có trong chapter, không dùng
            // existing.size()+1 — nếu 1 trang ở giữa từng bị xóa, size() sẽ tính
            // thiếu và sinh ra PageNumber trùng với trang đang tồn tại.
            int nextNum = existing.stream().mapToInt(MangaPage::getPageNumber).max().orElse(0) + 1;

            MangaPage page = new MangaPage();
            page.setId(pageId);
            page.setChapter(chapter);
            page.setPageNumber(nextNum);
            page.setStatus("unfinish");
            page.setPageType(pageType);
            page.setScript(pageScript.trim());
            mangaPageRepository.save(page);

            User user = (User) session.getAttribute("user");
            if (user != null) {
                activityLogService.log(user.getId(), "create-page",
                        "Đã tạo trang " + nextNum + " trong chapter \"" + chapter.getChapterName() + "\"");
            }

            result.put("status", "success");
            result.put("pageId", pageId);
            result.put("pageNumber", nextNum);
            result.put("message", "Thêm trang thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Sửa lại kịch bản của 1 trang đã tạo")
    @PostMapping("/myseries/{seriesId}/{chapterId}/{pageId}/editscript")
    @ResponseBody
    public Map<String, Object> editPageScript(@PathVariable String seriesId,
            @PathVariable String chapterId,
            @PathVariable String pageId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null || page.getChapter() == null || !page.getChapter().getId().equals(chapterId)) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pageId);
            return result;
        }

        User user = (User) session.getAttribute("user");
        if (!isOwnSeries(page.getChapter().getSeries(), user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền sửa trang này!");
            return result;
        }

        if (page.getChapter().getSeries() != null && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
            return result;
        }

        if ("finish".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Trang đã hoàn thành, không thể sửa kịch bản!");
            return result;
        }

        String script = body != null ? body.get("script") : null;
        if (script == null || script.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập kịch bản trang!");
            return result;
        }
        if (script.trim().length() > 1000) {
            result.put("status", "error");
            result.put("message", "Kịch bản trang tối đa 1000 chữ!");
            return result;
        }

        try {
            page.setScript(script.trim());
            mangaPageRepository.save(page);

            if (user != null) {
                activityLogService.log(user.getId(), "edit-page-script",
                        "Đã sửa kịch bản trang " + page.getPageNumber() + " trong chapter \""
                                + page.getChapter().getChapterName() + "\"");
            }

            result.put("status", "success");
            result.put("message", "Đã lưu kịch bản trang!");
            result.put("script", page.getScript());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Xóa trang trong chapter (chỉ khi trang chưa giao việc và chưa hoàn thành)")
    @PostMapping("/myseries/{sid}/{cid}/{pid}/delete-page")
    @ResponseBody
    public Map<String, Object> deletePage(@PathVariable String sid, @PathVariable String cid,
            @PathVariable String pid, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        MangaPage page = mangaPageRepository.findById(pid).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pid);
            return result;
        }

        Chapter chapter = page.getChapter();
        if (chapter == null || !chapter.getId().equals(cid)) {
            result.put("status", "error");
            result.put("message", "Trang không thuộc chapter này!");
            return result;
        }

        if (!isOwnSeries(chapter.getSeries(), user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên trang này!");
            return result;
        }

        if (chapter.getSeries() != null && chapter.getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", chapter.getSeries().getLockMessage());
            return result;
        }


        String pageStatus = page.getStatus();

        // Trang đang được giao việc cho assistant (đang có task dở)
        if ("intask".equals(pageStatus)) {
            result.put("status", "error");
            result.put("message", "Trang này đang được giao cho trợ lý, không thể xóa!");
            return result;
        }

        // Trợ lý đã nộp bài, đang chờ mangaka duyệt
        if ("done".equals(pageStatus)) {
            result.put("status", "error");
            result.put("message", "Trang này đang chờ duyệt bài trợ lý nộp, không thể xóa!");
            return result;
        }

        // Trang đã được đánh dấu hoàn thành
        if ("finish".equals(pageStatus)) {
            result.put("status", "error");
            result.put("message", "Trang này đã được đánh dấu hoàn thành, không thể xóa!");
            return result;
        }


        // Tới đây pageStatus chỉ còn có thể là "unfinish" → cho phép xóa
        try {
            List<Submission> subs = submissionRepository.findByPageIdId(pid);
            if (!subs.isEmpty()) {
                submissionRepository.deleteAll(subs);
            }
            mangaPageRepository.delete(page);

            activityLogService.log(user.getId(), "delete-page",
                    "Đã xóa trang " + page.getPageNumber() + " trong chapter \"" + chapter.getChapterName() + "\"");

            result.put("status", "success");
            result.put("message", "Đã xóa trang thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy thông tin submission")
    @GetMapping("/submission/{id}/data")
    @ResponseBody
    public Map<String, Object> getSubmissionData(@PathVariable String id, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Submission submission = submissionRepository.findById(id).orElse(null);
        if (submission == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy submission: " + id);
            return result;
        }
        if (!isOwnSubmission(submission, (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem bài nộp này!");
            return result;
        }
        String seriesId = submission.getPageId().getChapter().getSeries().getId();
        String chapterId = submission.getPageId().getChapter().getId();
        result.put("status", "success");
        result.put("submission", submission);
        result.put("seriesId", seriesId);
        result.put("chapterId", chapterId);
        result.put("returnUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
        return result;
    }

    @Operation(summary = "[SWAGGER] Cập nhật trạng thái submission")
    @PostMapping("/submission/{id}/submit/data")
    @ResponseBody
    public Map<String, Object> updateStatusData(@PathVariable String id,
            @RequestParam String status, @RequestParam String comment, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Submission submission = submissionRepository.findById(id).orElse(null);
        if (submission == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy submission: " + id);
            return result;
        }
        if (!isOwnSubmission(submission, (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên bài nộp này!");
            return result;
        }
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        if (!"pass".equals(normalizedStatus) && !"unfinish".equals(normalizedStatus)) {
            result.put("status", "error");
            result.put("message", "Trạng thái không hợp lệ");
            return result;
        }
        try {
            submission.setStatus(normalizedStatus);
            submission.setComment(comment);
            submissionRepository.save(submission);
            result.put("status", "success");
            result.put("message", "Cập nhật thành công!");
            result.put("seriesId", submission.getPageId().getChapter().getSeries().getId());
            result.put("chapterId", submission.getPageId().getChapter().getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // Thêm vào trong MangakaController
    @GetMapping("/{mangakaId}/assistants")
    @ResponseBody
    public List<Assistant> getAssistants(@PathVariable String mangakaId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (user == null || mangaka == null || mangaka.getUser() == null
                || !mangaka.getUser().getId().equals(user.getId())) {
            return List.of();
        }
        return assistantRepository.findByMangakaId(mangakaId);
    }

    @Operation(summary = "[SWAGGER] Lấy dữ liệu 1 trang để mở màn vẽ")
    @GetMapping("/myseries/{sid}/{cid}/{pid}/edit/data")
    @ResponseBody
    public Map<String, Object> getPageEditData(@PathVariable String sid, @PathVariable String cid,
            @PathVariable String pid, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        MangaPage page = mangaPageRepository.findById(pid).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pid);
            return result;
        }
        if (page.getChapter() == null || !isOwnSeries(page.getChapter().getSeries(), (User) session.getAttribute("user"))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem trang này!");
            return result;
        }
        result.put("status", "success");
        result.put("page", page);
        // Cho phép tác giả chọn nạp "bản tác giả giao" (assignedFilePath) hoặc
        // "bản trợ lý nộp" (filePath) của vòng gần nhất khi mở trang vẽ để sửa.
        submissionRepository.findTopByPageIdIdOrderByIdDesc(pid).ifPresent(sub -> {
            result.put("assignedFilePath", sub.getAssignedFilePath());
            result.put("submittedFilePath", sub.getFilePath());
        });
        return result;
    }

    @Operation(summary = "[SWAGGER] Lấy danh sách submission intask của các assistant thuộc mangaka")
    @GetMapping("/{mangakaId}/assistant-tasks")
    @ResponseBody
    public Map<String, Object> getAssistantTasks(@PathVariable String mangakaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (mangaka == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy mangaka: " + mangakaId);
            return result;
        }
        User requester = (User) session.getAttribute("user");
        if (mangaka.getUser() == null || requester == null
                || !mangaka.getUser().getId().equals(requester.getId())) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem dữ liệu này!");
            return result;
        }

        List<Submission> submissions = submissionRepository
                .findByAssistant_Mangaka_IdAndStatus(mangakaId, "intask");

        List<Map<String, Object>> tasks = submissions.stream().map(sub -> {
            Map<String, Object> task = new HashMap<>();
            task.put("submissionId", sub.getId());
            task.put("deadline", sub.getDeadline());
            task.put("submissionFilePath", sub.getFilePath());

            // Assistant info
            Assistant assistant = sub.getAssistant();
            task.put("assistantName", assistant != null && assistant.getUser() != null
                    ? assistant.getUser().getFullname()
                    : "Không rõ");

            // Page info
            MangaPage page = sub.getPageId();
            task.put("pageNumber", page != null ? page.getPageNumber() : null);
            task.put("pageFilePath", page != null ? page.getFilePath() : null);

            // Chapter info
            Chapter chapter = page != null ? page.getChapter() : null;
            task.put("chapterNumber", chapter != null ? chapter.getChapterNumber() : null);

            // Series info
            Series series = chapter != null ? chapter.getSeries() : null;
            task.put("seriesName", series != null ? series.getSeriesName() : "Không rõ");

            return task;
        }).toList();

        result.put("status", "success");
        result.put("tasks", tasks);
        return result;
    }
}
