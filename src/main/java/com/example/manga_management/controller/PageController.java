package com.example.manga_management.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.FrameTask;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.SubmissionVersion;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.FrameTaskRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.SubmissionVersionRepository;
import com.example.manga_management.service.ActivityLogService;
import com.example.manga_management.service.FileStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/page")
@Tag(name = "Page", description = "Giao việc vẽ trang cho trợ lý, duyệt bài, và nhận xét của tantou trên từng trang")
public class PageController {

    @Autowired
    private final AssistantRepository assistantRepository;
    @Autowired
    private final SubmissionRepository submissionRepository;
    @Autowired
    private final MangaPageRepository mangaPageRepository;
    @Autowired
    private final FrameTaskRepository frameTaskRepository;
    @Autowired
    private final SubmissionVersionRepository submissionVersionRepository;
    private final NotificationController notificationController;
    private final ActivityLogService activityLogService;
    private final FileStorageService fileStorageService;

    public PageController(AssistantRepository assistantRepository, SubmissionRepository submissionRepository,
            MangaPageRepository mangaPageRepository, FrameTaskRepository frameTaskRepository,
            SubmissionVersionRepository submissionVersionRepository,
            NotificationController notificationController, ActivityLogService activityLogService,
            FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.assistantRepository = assistantRepository;
        this.submissionRepository = submissionRepository;
        this.mangaPageRepository = mangaPageRepository;
        this.frameTaskRepository = frameTaskRepository;
        this.submissionVersionRepository = submissionVersionRepository;
        this.notificationController = notificationController;
        this.activityLogService = activityLogService;
    }

    /**
     * Copy một ảnh đã lưu sang tên file riêng của vòng giao việc, để bản
     * của vòng đó không bị lần lưu/nộp sau ghi đè. Trả về đường dẫn tương đối đã
     * lưu, hoặc null nếu không có ảnh nguồn.
     */
    private String snapshotVersionImage(String sourceRelPath, String submissionId, int roundNo, String kind) {
        if (sourceRelPath == null || sourceRelPath.isBlank()) {
            return null;
        }
        try {
            String fileName = submissionId + "_v" + roundNo + "_" + kind + ".png";
            return fileStorageService.copy(sourceRelPath, FileStorageService.DIR_SUBMISSION, fileName);
        } catch (IOException e) {
            // Ảnh phiên bản chỉ phục vụ tra cứu lịch sử — hỏng ảnh không được phép
            // chặn thao tác giao việc/duyệt bài đang diễn ra.
            return null;
        }
    }

    /** Mở một vòng giao việc mới và ghi vào lịch sử phiên bản. */
    private SubmissionVersion openVersion(Submission submission, int roundNo, String assignComment,
            LocalDateTime deadline) {
        SubmissionVersion version = new SubmissionVersion();
        version.setSubmission(submission);
        version.setRoundNo(roundNo);
        version.setAssignComment(assignComment);
        version.setDeadline(deadline);
        version.setAssignedAt(LocalDateTime.now());
        version.setResult(SubmissionVersion.RESULT_PENDING);
        version.setAssignedFilePath(
                snapshotVersionImage(submission.getAssignedFilePath(), submission.getId(), roundNo, "assigned"));
        return submissionVersionRepository.save(version);
    }

    /** Chốt vòng đang mở của submission (khi tác giả duyệt hoặc giao lại). */
    private void closeVersion(Submission submission, String result, String reviewComment) {
        submissionVersionRepository.findTopBySubmission_IdOrderByRoundNoDesc(submission.getId())
                .filter(v -> SubmissionVersion.RESULT_PENDING.equals(v.getResult()))
                .ifPresent(v -> {
                    // Giữ lại đúng ảnh trợ lý đã nộp ở vòng này trước khi vòng sau
                    // ghi đè /Submission/{id}.png.
                    if (v.getSubmittedFilePath() == null) {
                        v.setSubmittedFilePath(snapshotVersionImage(submission.getFilePath(), submission.getId(),
                                v.getRoundNo(), "submitted"));
                    }
                    v.setResult(result);
                    v.setReviewComment(reviewComment);
                    v.setReviewedAt(LocalDateTime.now());
                    submissionVersionRepository.save(v);
                });
    }

    /** Mô tả ngắn gọn 1 trang, dùng chung cho nội dung thông báo/log. */
    private String describePage(MangaPage page) {
        if (page.getChapter() == null) {
            return "trang " + page.getId();
        }
        String seriesName = page.getChapter().getSeries() != null
                ? page.getChapter().getSeries().getSeriesName() : "—";
        return "trang " + page.getPageNumber() + " (Chapter " + page.getChapter().getChapterNumber()
                + " - " + seriesName + ")";
    }

    /** Mangaka gọi request này có phải chủ sở hữu series chứa trang này không. */
    private boolean isOwnerMangaka(MangaPage page, User user) {
        return page != null && user != null && page.getChapter() != null
                && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().getProposal() != null
                && page.getChapter().getSeries().getProposal().getMangaka() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getUser() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getUser().getId().equals(user.getId());
    }

    /** Tantou gọi request này có phải người phụ trách mangaka của series này không. */
    private boolean isAssignedTantou(MangaPage page, User user) {
        return page != null && user != null && page.getChapter() != null
                && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().getProposal() != null
                && page.getChapter().getSeries().getProposal().getMangaka() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getEditor() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getEditor().getUser() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getEditor().getUser().getId()
                        .equals(user.getId());
    }

    /** Assistant gọi request này có đang là người được giao vòng task gần nhất của trang không. */
    private boolean isCurrentAssignedAssistant(MangaPage page, User user) {
        if (page == null || user == null) {
            return false;
        }
        Optional<Submission> latest = submissionRepository.findTopByPageIdIdOrderByIdDesc(page.getId());
        return latest.isPresent() && "intask".equals(latest.get().getStatus())
                && latest.get().getAssistant() != null
                && latest.get().getAssistant().getUser() != null
                && latest.get().getAssistant().getUser().getId().equals(user.getId());
    }

    /** Assistant gọi request này có phải người được giao trong vòng task gần nhất của trang không (kể cả đã nộp). */
    private boolean wasAssignedAssistant(MangaPage page, User user) {
        if (page == null || user == null) {
            return false;
        }
        return submissionRepository.findTopByPageIdIdOrderByIdDesc(page.getId())
                .filter(s -> s.getAssistant() != null && s.getAssistant().getUser() != null
                        && s.getAssistant().getUser().getId().equals(user.getId()))
                .isPresent();
    }

    /**
     * Chỉ về "untask" khi tất cả task của assistant đã được duyệt hết (không còn
     * submission intask hoặc done chờ duyệt).
     */
    private void refreshAssistantStatus(Assistant assistant) {
        boolean hasUnapprovedTask = !submissionRepository
                .findByAssistant_IdAndStatus(assistant.getId(), "intask").isEmpty()
                || !submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done").isEmpty();
        assistant.setStatus(hasUnapprovedTask ? "intask" : "untask");
        assistantRepository.save(assistant);
    }

    @Operation(summary = "Lưu ảnh bản vẽ (base64) cho một trang")
    @PostMapping("/{pageId}/savefile")
    @ResponseBody
    public Map<String, String> savePageFile(@PathVariable String pageId,
            @RequestBody Map<String, String> body, HttpSession session) {
        Map<String, String> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            if (!isOwnerMangaka(page, user) && !isCurrentAssignedAssistant(page, user)) {
                result.put("status", "error");
                result.put("message", "Bạn không có quyền chỉnh sửa trang này!");
                return result;
            }

            if (page.getChapter() != null && page.getChapter().getSeries() != null
                    && page.getChapter().getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", page.getChapter().getSeries().getLockMessage());
                return result;
            }

            String base64 = body.get("imageBase64");
            if (base64 != null && base64.contains(",")) {
                base64 = base64.split(",")[1];
            }
            if (base64 == null || base64.isBlank()) {
                result.put("status", "error");
                result.put("message", "imageBase64 trống");
                return result;
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64);
            String savedPath = fileStorageService.saveBytes(FileStorageService.DIR_MANGA_PAGE,
                    pageId + ".png", imageBytes, FileStorageService.IMAGE_EXTENSIONS);

            page.setFilePath(savedPath);
            // Không ép status về "unfinish" ở đây — nếu trang đã có task
            // (intask/done/finish), lưu bản vẽ (vd sửa lại sau khi duyệt) không
            // được phép làm mất trạng thái đó. Trang mới tạo vốn đã "unfinish"
            // sẵn nên không cần set lại.
            mangaPageRepository.save(page);

            String chapterId = page.getChapter().getId();
            String seriesId = page.getChapter().getSeries().getId();

            result.put("status", "success");
            result.put("message", "Lưu trang thành công!");
            result.put("redirectUrl", "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
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

    @Operation(summary = "Thông tin submission (bài nộp) gần nhất của một trang")
    @GetMapping("/{pageId}/submission")
    @ResponseBody
    public Map<String, Object> getPageSubmission(@PathVariable String pageId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        // Chỉ chủ series, tantou phụ trách hoặc trợ lý được giao trang này mới xem được.
        User requester = (User) session.getAttribute("user");
        MangaPage requestedPage = mangaPageRepository.findById(pageId).orElse(null);
        if (requestedPage == null || (!isOwnerMangaka(requestedPage, requester)
                && !isAssignedTantou(requestedPage, requester)
                && !wasAssignedAssistant(requestedPage, requester))) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền xem thông tin trang này!");
            return result;
        }

        Optional<Submission> optSubmission = submissionRepository.findTopByPageIdIdOrderByIdDesc(pageId);
        if (optSubmission.isEmpty()) {
            result.put("status", "success");
            result.put("hasSubmission", false);
            return result;
        }

        Submission submission = optSubmission.get();
        result.put("status", "success");
        result.put("hasSubmission", true);
        result.put("assistantName", submission.getAssistant().getUser().getFullname());
        result.put("submissionStatus", submission.getStatus());
        return result;
    }

    @Operation(summary = "Mangaka giao vẽ 1 trang cho trợ lý (kèm deadline + note từng frame)")
    @PostMapping("/{pageId}/assign")
    @ResponseBody
    @Transactional
    public Map<String, Object> assignPage(@PathVariable String pageId,
            @RequestBody AssignPageRequest body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            if (!isOwnerMangaka(page, user)) {
                result.put("status", "error");
                result.put("message", "Bạn không có quyền giao việc trên trang này!");
                return result;
            }

            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                result.put("status", "error");
                result.put("message", "Chỉ có thể giao việc khi chapter đang ở trạng thái unfinish");
                return result;
            }

            if (page.getChapter().getSeries() != null
                    && page.getChapter().getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", page.getChapter().getSeries().getLockMessage());
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            // Trang có thể trải qua nhiều vòng giao việc (nhiều assistant khác nhau
            // theo thời gian) — chỉ chặn khi task gần nhất còn dang dở (đang làm
            // hoặc đã nộp nhưng chưa duyệt/giao lại). Sau khi đã duyệt xong thì mở
            // ra cho vòng giao việc mới.
            Optional<Submission> latestSubmissionOpt = submissionRepository.findTopByPageIdIdOrderByIdDesc(pageId);
            if (latestSubmissionOpt.isPresent()) {
                String latestStatus = latestSubmissionOpt.get().getStatus();
                if ("intask".equals(latestStatus)) {
                    result.put("status", "error");
                    result.put("message", "Trang này đang được giao");
                    return result;
                }
                if ("done".equals(latestStatus)) {
                    result.put("status", "error");
                    result.put("message", "Trang này đang chờ duyệt, hãy Duyệt hoặc Giao lại trước khi giao task mới");
                    return result;
                }
            }

            String assistantId = body != null ? body.assistantId() : null;
            String comment = body != null ? body.comment() : null;
            String deadlineStr = body != null ? body.deadline() : null;
            List<String> frameNotes = body != null ? body.frameNotes() : null;

            Assistant assistant = assistantRepository.findById(assistantId).orElse(null);
            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy assistant: " + assistantId);
                return result;
            }
            if (assistant.getMangaka() == null || assistant.getMangaka().getUser() == null
                    || !assistant.getMangaka().getUser().getId().equals(user.getId())) {
                result.put("status", "error");
                result.put("message", "Trợ lý này không thuộc quyền quản lý của bạn!");
                return result;
            }

            if (deadlineStr == null || deadlineStr.isBlank()) {
                result.put("status", "error");
                result.put("message", "Deadline là bắt buộc");
                return result;
            }

            // Yêu cầu chung của cả task là bắt buộc — note từng frame chỉ nói việc
            // của riêng frame đó, không thay được phần mô tả trợ lý cần làm gì
            // cho toàn trang.
            if (comment == null || comment.isBlank()) {
                result.put("status", "error");
                result.put("message", "Vui lòng nhập yêu cầu / ghi chú cho trợ lý!");
                return result;
            }
            if (comment.length() > 1000) {
                result.put("status", "error");
                result.put("message", "Yêu cầu / ghi chú tối đa 1000 chữ!");
                return result;
            }

            if (frameNotes == null || frameNotes.isEmpty()) {
                result.put("status", "error");
                result.put("message", "Vui lòng chọn số frame và nhập note cho từng frame!");
                return result;
            }
            if (frameNotes.size() > 20) {
                result.put("status", "error");
                result.put("message", "Số frame tối đa là 20 cho mỗi task!");
                return result;
            }
            for (Object note : frameNotes) {
                if (note != null && note.toString().length() > 1000) {
                    result.put("status", "error");
                    result.put("message", "Note mỗi frame tối đa 1000 chữ!");
                    return result;
                }
            }

            LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
            if (!deadline.isAfter(LocalDateTime.now())) {
                result.put("status", "error");
                result.put("message", "Deadline phải sau ngày hiện tại");
                return result;
            }

            // Luôn tạo submission mới cho mỗi lần giao việc — không ghi đè submission
            // cũ đã duyệt/hoàn thành, để giữ lại lịch sử từng vòng giao việc (vd
            // ass1 vẽ bối cảnh xong duyệt, rồi giao vòng mới cho ass2 viết thoại).
            String lastId = submissionRepository.findTopByOrderByIdDesc()
                    .map(Submission::getId).orElse("SUB0000");
            int num = Integer.parseInt(lastId.replaceAll("[^0-9]", "")) + 1;
            String newId = "SUB" + String.format("%04d", num);
            Submission submission = new Submission();
            submission.setId(newId);

            submission.setPageId(page);
            submission.setAssistant(assistant);
            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");

            // Snapshot BẤT BIẾN "bản tác giả giao" — lưu ở file riêng {id}_assigned.png
            // để lần trợ lý lưu bài sau này (ghi /Submission/{id}.png) không đè lên.
            if (page.getFilePath() != null && !page.getFilePath().isBlank()) {
                String fileName = submission.getId() + "_assigned.png";
                String assignedPath = fileStorageService.copy(page.getFilePath(),
                        FileStorageService.DIR_SUBMISSION, fileName);
                if (assignedPath == null) {
                    throw new IOException("Không tìm thấy ảnh trang: " + page.getFilePath());
                }
                submission.setAssignedFilePath(assignedPath);
            }

            submission.setCurrentRound(1);
            submissionRepository.save(submission);

            // Vòng 1 của task này — mở bản ghi lịch sử phiên bản.
            openVersion(submission, 1, comment, deadline);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            // Lưu note công việc cho từng frame của vòng giao việc mới này
            Optional<FrameTask> lastFrameTask = frameTaskRepository.findTopByOrderByIdDesc();
            int maxFrameTaskId = 0;
            if (lastFrameTask.isPresent()) {
                maxFrameTaskId = Integer.parseInt(lastFrameTask.get().getId().substring(2));
            }
            int frameNumber = 1;
            for (Object note : frameNotes) {
                FrameTask frameTask = new FrameTask();
                frameTask.setId("FT" + String.format("%05d", ++maxFrameTaskId));
                frameTask.setPage(page);
                frameTask.setSubmission(submission);
                frameTask.setFrameNumber(frameNumber++);
                frameTask.setContent(note == null ? "" : note.toString());
                frameTaskRepository.save(frameTask);
            }

            // Giao việc xong thì assistant chuyển sang intask
            assistant.setStatus("intask");
            assistantRepository.save(assistant);

            if (assistant.getUser() != null) {
                notificationController.send(null, assistant.getUser().getId(),
                        "Bạn được giao " + describePage(page) + ", deadline "
                                + deadline.toLocalDate() + " " + deadline.toLocalTime() + ".",
                        "/manga/assistant");
            }

            result.put("status", "success");
            result.put("message", "Giao việc thành công!");
            result.put("submissionId", submission.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "Đánh dấu 1 trang đã hoàn thành xong (kết thúc vòng làm việc)")
    @PostMapping("/{pageId}/finish")
    @ResponseBody
    public Map<String, Object> finishPage(@PathVariable String pageId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang!");
            return result;
        }
        if (!isOwnerMangaka(page, user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên trang này!");
            return result;
        }
        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
            return result;
        }
        if ("finish".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Trang đã hoàn thành rồi!");
            return result;
        }
        if ("intask".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Không thể hoàn thành vì trợ lý đang làm việc này!");
            return result;
        }
        if ("done".equals(page.getStatus())) {
            Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByIdDesc(pageId);
            if (subOpt.isPresent() && "done".equals(subOpt.get().getStatus())) {
                result.put("status", "error");
                result.put("message", "Bạn phải ấn nút Duyệt trước khi hoàn thành!");
                return result;
            }
        }

        page.setStatus("finish");
        mangaPageRepository.save(page);

        result.put("status", "success");
        result.put("message", "Đã hoàn thành trang!");
        result.put("pageId", pageId);
        return result;
    }

    @Operation(summary = "Mangaka duyệt bài trợ lý vừa nộp cho 1 trang")
    @PostMapping("/{pageId}/approve-done")
    @ResponseBody
    public Map<String, Object> approvePageDone(@PathVariable String pageId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang!");
            return result;
        }
        if (!isOwnerMangaka(page, user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên trang này!");
            return result;
        }
        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
            return result;
        }

        Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByIdDesc(pageId);
        if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
            result.put("status", "error");
            result.put("message", "Trợ lý chưa nộp bài!");
            return result;
        }

        Submission sub = subOpt.get();
        sub.setStatus("finish");
        sub.setApprovedAt(LocalDateTime.now());
        submissionRepository.save(sub);

        // Chốt vòng đang mở là "đã duyệt" — giữ nguyên đúng ảnh đã được duyệt để
        // sau này còn tra lại tác giả đã đồng ý với bản nào.
        closeVersion(sub, SubmissionVersion.RESULT_APPROVED, null);

        // Duyệt xong MỚI cập nhật bài trợ lý vào ảnh TRANG CHÍNH THỨC (pageId.png).
        // Trước khi duyệt, bài chỉ nằm trong submission nên không đè trang tác giả.
        if (sub.getFilePath() != null && !sub.getFilePath().isBlank()) {
            try {
                String pagePath = fileStorageService.copy(sub.getFilePath(),
                        FileStorageService.DIR_MANGA_PAGE, page.getId() + ".png");
                if (pagePath != null) {
                    page.setFilePath(pagePath);
                    mangaPageRepository.save(page);
                }
            } catch (IOException e) {
                // Không chặn việc duyệt nếu copy ảnh lỗi.
            }
        }

        // Nếu assistant không còn task nào chưa duyệt thì chuyển về untask
        if (sub.getAssistant() != null) {
            refreshAssistantStatus(sub.getAssistant());
            if (sub.getAssistant().getUser() != null) {
                notificationController.send(null, sub.getAssistant().getUser().getId(),
                        "Bài làm " + describePage(page) + " của bạn đã được duyệt!",
                        "/manga/assistant");
            }
        }

        result.put("status", "success");
        result.put("message", "Đã duyệt! Bấm Hoàn thành để kết thúc trang.");
        result.put("pageId", pageId);
        return result;
    }

    // @PostMapping("/{pageId}/reject-done")
    // @ResponseBody
    // public Map<String, Object> rejectPageDone(@PathVariable String pageId) {
    //     Map<String, Object> result = new HashMap<>();
    //     MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
    //     if (page == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy trang!");
    //         return result;
    //     }
    //     Optional<Submission> subOpt = submissionRepository.findByPageIdId(pageId);
    //     if (subOpt.isEmpty() || !"done".equals(subOpt.get().getStatus())) {
    //         result.put("status", "error");
    //         result.put("message", "Trợ lý chưa nộp bài!");
    //         return result;
    //     }
    //     Submission sub = subOpt.get();
    //     sub.setStatus("intask");
    //     submissionRepository.save(sub);
    //     page.setStatus("intask");
    //     mangaPageRepository.save(page);
    //     result.put("status", "success");
    //     result.put("message", "Đã giao lại cho trợ lý!");
    //     result.put("pageId", pageId);
    //     return result;
    // }
    @Operation(summary = "Giao lại trang cho đúng trợ lý cũ (đổi deadline/note, không đổi người)")
    @PostMapping("/{pageId}/reassign")
    @ResponseBody
    public Map<String, Object> reassignPage(@PathVariable String pageId,
            @RequestBody ReassignPageRequest body,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập");
                return result;
            }

            String submissionId = body != null ? body.submissionId() : null;
            String comment = body != null ? body.comment() : null;
            String reviewComment = body != null ? body.reviewComment() : null;
            String deadlineStr = body != null ? body.deadline() : null;

            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy submission: " + submissionId);
                return result;
            }

            if (submission.getPageId() == null || !pageId.equals(submission.getPageId().getId())) {
                result.put("status", "error");
                result.put("message", "Submission không thuộc page này");
                return result;
            }

            MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
            if (page == null) {
                result.put("status", "error");
                result.put("message", "Không tìm thấy page: " + pageId);
                return result;
            }

            if (!isOwnerMangaka(page, user)) {
                result.put("status", "error");
                result.put("message", "Bạn không có quyền thao tác trên trang này!");
                return result;
            }

            if (page.getChapter() == null || !"unfinish".equals(page.getChapter().getStatus())) {
                result.put("status", "error");
                result.put("message", "Chỉ có thể giao lại khi chapter đang ở trạng thái unfinish");
                return result;
            }

            if (page.getChapter().getSeries() != null
                    && page.getChapter().getSeries().isLocked()) {
                result.put("status", "error");
                result.put("message", page.getChapter().getSeries().getLockMessage());
                return result;
            }

            if ("finish".equals(page.getStatus())) {
                result.put("status", "error");
                result.put("message", "Trang này đã được đánh dấu hoàn thành");
                return result;
            }

            // Chỉ được "giao lại" submission đang chờ duyệt ("done") — nếu đã
            // "finish" (đã duyệt/tính lương) hoặc đang "intask" thì không được phép
            // đẩy lùi trạng thái, tránh làm sai lệch dữ liệu đã duyệt/lương.
            if (!"done".equals(submission.getStatus())) {
                result.put("status", "error");
                result.put("message", "Chỉ có thể giao lại task đang chờ duyệt (trạng thái 'done')!");
                return result;
            }

            // "Giao lại" luôn giữ nguyên đúng trợ lý đang làm task này — không nhận
            // assistantId từ client để tránh bị đổi sang người khác (muốn giao cho
            // người khác thì dùng "Giao việc" để tạo task mới).
            Assistant assistant = submission.getAssistant();
            if (assistant == null) {
                result.put("status", "error");
                result.put("message", "Task này chưa có trợ lý để giao lại");
                return result;
            }

            if (deadlineStr == null || deadlineStr.isBlank()) {
                result.put("status", "error");
                result.put("message", "Deadline là bắt buộc");
                return result;
            }

            // Giao lại là hình thức từ chối bài của trợ lý, nên bắt buộc nêu lý do —
            // cùng nguyên tắc với việc Tantou phải nhập lý do khi từ chối đề xuất
            // hay chapter. Không có lý do thì trợ lý không biết bản vừa nộp sai ở đâu.
            if (reviewComment == null || reviewComment.isBlank()) {
                result.put("status", "error");
                result.put("message", "Vui lòng nêu lý do bản vừa nộp chưa đạt!");
                return result;
            }
            if (reviewComment.length() > 1000) {
                result.put("status", "error");
                result.put("message", "Lý do chưa đạt tối đa 1000 chữ!");
                return result;
            }
            // Yêu cầu cho vòng mới cũng bắt buộc: lý do nói bản cũ sai ở đâu, còn
            // yêu cầu nói vòng này phải làm gì. Thiếu vế thứ hai thì trợ lý biết
            // mình sai nhưng không biết phải sửa tới đâu là đạt.
            if (comment == null || comment.isBlank()) {
                result.put("status", "error");
                result.put("message", "Vui lòng nhập yêu cầu cho vòng làm lại!");
                return result;
            }
            if (comment.length() > 1000) {
                result.put("status", "error");
                result.put("message", "Yêu cầu cho vòng mới tối đa 1000 chữ!");
                return result;
            }

            LocalDateTime deadline = LocalDateTime.parse(deadlineStr);
            if (!deadline.isAfter(LocalDateTime.now())) {
                result.put("status", "error");
                result.put("message", "Deadline phải sau ngày hiện tại");
                return result;
            }

            // CHỐT VÒNG CŨ TRƯỚC KHI ĐỘNG VÀO submission.filePath. Ở phiên bản cũ,
            // đoạn dưới set filePath = null và ghi đè {id}_assigned.png, nên bản trợ
            // lý đã nộp ở vòng này mất hẳn — tác giả không còn đối chiếu được đã yêu
            // cầu sửa gì so với bản nào. Giờ ảnh vòng cũ được copy sang tên riêng
            // {id}_v{n}_submitted.png trước, rồi vòng mới mới được mở.
            closeVersion(submission, SubmissionVersion.RESULT_REASSIGNED, reviewComment);

            // Chọn bản nền giao lại: bản được chọn trở thành "bản tác giả giao" mới
            // của vòng này; "bản trợ lý nộp" reset về Chưa nộp; ảnh trang chính thức
            // cập nhật theo bản được chọn.
            String base = body != null ? body.base() : null;
            if (base != null && !base.isBlank()) {
                String chosenPath;
                if ("assigned".equals(base)) {
                    chosenPath = submission.getAssignedFilePath();
                } else if ("submitted".equals(base)) {
                    chosenPath = submission.getFilePath();
                } else { // "current" = ảnh trang hiện tại (bản tác giả vừa vẽ)
                    chosenPath = page.getFilePath();
                }

                if (chosenPath != null && !chosenPath.isBlank()) {
                    try {
                        if (fileStorageService.exists(chosenPath)) {
                            // 1) Bản được chọn → "bản tác giả giao" mới (bất biến).
                            String assignedRel = fileStorageService.copy(chosenPath,
                                    FileStorageService.DIR_SUBMISSION, submission.getId() + "_assigned.png");
                            submission.setAssignedFilePath(assignedRel);

                            // 2) Ảnh trang chính thức = bản được chọn (bỏ qua nếu đã trùng).
                            String pageRel = "/MangaPage/" + page.getId() + ".png";
                            if (!pageRel.equals(chosenPath)) {
                                fileStorageService.copy(chosenPath, FileStorageService.DIR_MANGA_PAGE,
                                        page.getId() + ".png");
                                page.setFilePath(pageRel);
                            }
                        }
                    } catch (IOException e) {
                        // Không chặn giao lại nếu copy ảnh lỗi.
                    }
                }
                // 3) Reset "bản trợ lý nộp" cho vòng mới.
                submission.setFilePath(null);
            }

            submission.setComment(comment);
            submission.setDeadline(deadline);
            submission.setStatus("intask");
            submission.setSubmittedAt(null);
            int nextRound = (submission.getCurrentRound() == null ? 1 : submission.getCurrentRound()) + 1;
            submission.setCurrentRound(nextRound);
            submissionRepository.save(submission);

            // Mở vòng mới trong lịch sử: ghi lại bản nền tác giả giao, yêu cầu sửa
            // và deadline của riêng vòng này.
            openVersion(submission, nextRound, comment, deadline);

            page.setStatus("intask");
            mangaPageRepository.save(page);

            assistant.setStatus("intask");
            assistantRepository.save(assistant);

            if (assistant.getUser() != null) {
                notificationController.send(null, assistant.getUser().getId(),
                        "Bạn được giao lại " + describePage(page) + ", deadline "
                                + deadline.toLocalDate() + " " + deadline.toLocalTime()
                                + ". Lý do chưa đạt: " + reviewComment,
                        "/manga/assistant");
            }
            activityLogService.log(user.getId(), "reassign-task",
                    "Đã giao lại " + describePage(page) + " cho " + assistant.getUser().getFullname());

            result.put("status", "success");
            result.put("message", "Đã giao lại thành công!");
            result.put("submissionId", submissionId);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    /** Tantou nhận xét riêng cho 1 trang (khác kịch bản) — mangaka xem được, có thông báo + lưu lịch sử. */
    @Operation(summary = "Tantou lưu nhận xét cho 1 trang (chặn nếu trang đã hoàn thành)")
    @PostMapping("/{pageId}/tantou-comment")
    @ResponseBody
    public Map<String, Object> saveTantouComment(@PathVariable String pageId,
            @RequestBody TantouCommentRequest body, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        MangaPage page = mangaPageRepository.findById(pageId).orElse(null);
        if (page == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy trang: " + pageId);
            return result;
        }

        if (!isAssignedTantou(page, user)) {
            result.put("status", "error");
            result.put("message", "Bạn không phải tantou phụ trách series này!");
            return result;
        }

        if ("finish".equals(page.getStatus())) {
            result.put("status", "error");
            result.put("message", "Trang đã hoàn thành, không thể thêm nhận xét!");
            return result;
        }

        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().isLocked()) {
            result.put("status", "error");
            result.put("message", page.getChapter().getSeries().getLockMessage());
            return result;
        }

        String comment = body != null ? body.comment() : null;
        if (comment == null || comment.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập nội dung nhận xét!");
            return result;
        }
        if (comment.trim().length() > 500) {
            result.put("status", "error");
            result.put("message", "Nhận xét tối đa 500 chữ!");
            return result;
        }

        page.setTantouComment(comment.trim());
        mangaPageRepository.save(page);

        if (page.getChapter() != null && page.getChapter().getSeries() != null
                && page.getChapter().getSeries().getProposal() != null
                && page.getChapter().getSeries().getProposal().getMangaka() != null
                && page.getChapter().getSeries().getProposal().getMangaka().getUser() != null) {
            String seriesId = page.getChapter().getSeries().getId();
            String chapterId = page.getChapter().getId();
            notificationController.send(null,
                    page.getChapter().getSeries().getProposal().getMangaka().getUser().getId(),
                    "Tantou vừa nhận xét " + describePage(page) + ": " + comment.trim(),
                    "/manga/mangaka/myseries/" + seriesId + "/" + chapterId);
        }

        activityLogService.log(user.getId(), "comment-page",
                "Đã nhận xét " + describePage(page) + ": " + comment.trim());

        result.put("status", "success");
        result.put("message", "Đã lưu nhận xét!");
        return result;
    }

    public record TantouCommentRequest(String comment) {
    }

    public record AssignPageRequest(String assistantId, String comment, String deadline, List<String> frameNotes) {
    }

    // base: bản nền giao lại cho trợ lý — "current" (trang tác giả vừa vẽ),
    // "assigned" (bản tác giả giao trước), "submitted" (bản trợ lý nộp). null = giữ nguyên.
    //
    // comment       = yêu cầu cho VÒNG MỚI (trợ lý đọc để biết phải làm gì tiếp).
    // reviewComment = lý do VÒNG CŨ chưa đạt (chốt vào lịch sử phiên bản của vòng
    //                 vừa đóng). Hai nội dung này phục vụ hai mục đích khác nhau
    //                 nên tách riêng — trước đây dùng chung một ô, khiến lịch sử
    //                 vòng cũ ghi nhầm yêu cầu của vòng sau.
    public record ReassignPageRequest(String submissionId, String comment, String reviewComment,
            String deadline, String base) {
    }
}
