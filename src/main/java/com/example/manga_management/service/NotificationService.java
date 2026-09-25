package com.example.manga_management.service;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Notification;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.SubmissionVersion;
import com.example.manga_management.entity.User;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.NotificationRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.SubmissionVersionRepository;
import com.example.manga_management.repository.VoteSessionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TYPE_GENERIC = "GENERIC";
    private static final String TYPE_DEADLINE_SOON = "DEADLINE_SOON";
    private static final String TYPE_DEADLINE_OVERDUE = "DEADLINE_OVERDUE";
    private static final String TYPE_AUTO_SUBMIT = "AUTO_SUBMIT";
    private static final String TYPE_CHAPTER_OVERDUE_CANCEL = "CHAPTER_OVERDUE_CANCEL";
    private static final String TYPE_CHAPTER_AUTO_SUBMIT = "CHAPTER_AUTO_SUBMIT";
    private static final String TYPE_SERIES_AUTO_STOPPED = "SERIES_AUTO_STOPPED";

    private final NotificationRepository notificationRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository submissionVersionRepository;
    private final AssistantRepository assistantRepository;
    private final MangaPageRepository mangaPageRepository;
    private final ChapterRepository chapterRepository;
    private final ProposalRepository proposalRepository;
    private final SeriesRepository seriesRepository;
    private final VoteSessionRepository voteSessionRepository;

    public List<Notification> getInbox(User user) {
        if (user == null) {
            return List.of();
        }
        return notificationRepository.findInbox(user.getId(), user.getRole());
    }

    public long getUnreadCount(User user) {
        if (user == null) {
            return 0L;
        }
        return notificationRepository.countUnread(user.getId(), user.getRole());
    }

    public Notification sendToUser(String userId, String content, String link) {
        return sendToUser(userId, content, link, TYPE_GENERIC, null);
    }

    public Notification sendToUser(String userId, String content, String link, String type, String referenceKey) {
        if (userId == null || userId.isBlank() || content == null || content.isBlank()) {
            return null;
        }

        if (type != null && referenceKey != null
                && notificationRepository.countByUserIdAndTypeAndReferenceKey(userId, type, referenceKey) > 0) {
            return null;
        }

        Notification note = Notification.builder()
                .userId(userId)
                .content(content)
                .link(link)
                .type(type)
                .referenceKey(referenceKey)
                .isRead(false)
                .build();
        return notificationRepository.save(note);
    }

    public Notification sendToRole(String role, String content, String link) {
        return sendToRole(role, content, link, TYPE_GENERIC, null);
    }

    public Notification sendToRole(String role, String content, String link, String type, String referenceKey) {
        if (role == null || role.isBlank() || content == null || content.isBlank()) {
            return null;
        }

        if (type != null && referenceKey != null
                && notificationRepository.countByRoleAndTypeAndReferenceKey(role, type, referenceKey) > 0) {
            return null;
        }

        Notification note = Notification.builder()
                .role(role)
                .content(content)
                .link(link)
                .type(type)
                .referenceKey(referenceKey)
                .isRead(false)
                .build();
        return notificationRepository.save(note);
    }

    public void markAllRead(User user) {
        if (user == null) {
            return;
        }
        List<Notification> list = notificationRepository.findInbox(user.getId(), user.getRole());
        list.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(list);
    }

    /**
     * Quá hạn deadline thì tự động nộp bài: submission intask -> done, page ->
     * done. Assistant vẫn giữ intask cho tới khi mangaka duyệt hết task.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void autoSubmitOverdueSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        List<Submission> overdueList = submissionRepository.findByStatusAndDeadlineBefore("intask", now);

        for (Submission submission : overdueList) {
            submission.setStatus("done");
            submission.setSubmittedAt(now);
            submissionRepository.save(submission);

            // Ghi mốc nộp vào vòng đang mở, nếu không timeline sẽ hiện "chưa nộp"
            // cho một task đã chuyển sang chờ duyệt.
            submissionVersionRepository.findTopBySubmission_IdOrderByRoundNoDesc(submission.getId())
                    .filter(v -> SubmissionVersion.RESULT_PENDING.equals(v.getResult())
                            && v.getSubmittedAt() == null)
                    .ifPresent(v -> {
                        v.setSubmittedAt(now);
                        submissionVersionRepository.save(v);
                    });

            if (submission.getPageId() != null) {
                submission.getPageId().setStatus("done");
                mangaPageRepository.save(submission.getPageId());
            }

            Assistant assistant = submission.getAssistant();
            if (assistant != null && assistant.getUser() != null) {
                sendToUser(assistant.getUser().getId(),
                        "Bài làm " + submission.getId() + " đã được tự động nộp do quá hạn deadline ("
                                + submission.getDeadline() + ")",
                        "/manga/assistant",
                        TYPE_AUTO_SUBMIT,
                        submission.getId());
            }
        }
    }

    /**
     * Chapter quá hạn nộp: mọi trang chưa "finish" bị tự động đánh dấu hoàn
     * thành, task đang làm dở bị hủy (báo tantou), và chapter tự động được nộp
     * lên BTV (status "finish" — giống hệt khi mangaka bấm "Submit lên BTV").
     *
     * Chapter chưa có trang nào thì không nộp được (giống luật submit thủ công),
     * để nguyên "unfinish" cho mangaka tự xử lý.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void autoFinishOverdueChapters() {
        LocalDateTime now = LocalDateTime.now();
        List<Chapter> overdueChapters = chapterRepository.findByStatusAndDeadlineBefore("unfinish", now);

        for (Chapter chapter : overdueChapters) {
            List<MangaPage> pages = mangaPageRepository.findByChapter(chapter);
            if (pages.isEmpty()) {
                continue;
            }

            for (MangaPage page : pages) {
                if ("finish".equals(page.getStatus())) {
                    continue;
                }

                Optional<Submission> subOpt = submissionRepository.findTopByPageIdIdOrderByIdDesc(page.getId());
                if (subOpt.isPresent() && "intask".equals(subOpt.get().getStatus())) {
                    Submission sub = subOpt.get();
                    sub.setStatus("cancelled");
                    submissionRepository.save(sub);

                    // Đóng luôn vòng đang mở trong lịch sử, nếu không nó sẽ mắc kẹt
                    // ở "đang chờ" vĩnh viễn trên timeline dù task đã bị huỷ.
                    submissionVersionRepository.findTopBySubmission_IdOrderByRoundNoDesc(sub.getId())
                            .filter(v -> SubmissionVersion.RESULT_PENDING.equals(v.getResult()))
                            .ifPresent(v -> {
                                v.setResult(SubmissionVersion.RESULT_CANCELLED);
                                v.setReviewComment("Bị huỷ tự động do chapter quá hạn nộp.");
                                v.setReviewedAt(LocalDateTime.now());
                                submissionVersionRepository.save(v);
                            });

                    String content = "Task " + sub.getId() + " (trang " + page.getPageNumber()
                            + ", chapter " + chapter.getChapterName() + ") đã bị hủy do chapter quá hạn nộp.";
                    sendToRole("tantou", content, "/manga/editor", TYPE_CHAPTER_OVERDUE_CANCEL, sub.getId());

                    if (sub.getAssistant() != null) {
                        refreshAssistantStatus(sub.getAssistant());
                    }
                }

                page.setStatus("finish");
                mangaPageRepository.save(page);
            }

            // Tự động nộp chapter lên BTV
            chapter.setStatus("finish");
            chapterRepository.save(chapter);
            notifyChapterAutoSubmitted(chapter);
        }
    }

    /** Báo cho tantou phụ trách + mangaka biết chapter đã bị tự động nộp do quá hạn. */
    private void notifyChapterAutoSubmitted(Chapter chapter) {
        if (chapter.getSeries() == null || chapter.getSeries().getProposal() == null
                || chapter.getSeries().getProposal().getMangaka() == null) {
            return;
        }
        Mangaka mangaka = chapter.getSeries().getProposal().getMangaka();
        String seriesName = chapter.getSeries().getSeriesName();

        if (mangaka.getEditor() != null && mangaka.getEditor().getUser() != null) {
            sendToUser(mangaka.getEditor().getUser().getId(),
                    "Chapter '" + chapter.getChapterName() + "' của series '" + seriesName
                            + "' đã quá hạn và được tự động nộp, đang chờ bạn duyệt.",
                    "/manga/tantou",
                    TYPE_CHAPTER_AUTO_SUBMIT,
                    chapter.getId());
        }

        if (mangaka.getUser() != null) {
            sendToUser(mangaka.getUser().getId(),
                    "Chapter '" + chapter.getChapterName() + "' của series '" + seriesName
                            + "' đã quá hạn nên được tự động nộp lên biên tập viên.",
                    "/manga/mangaka",
                    TYPE_CHAPTER_AUTO_SUBMIT,
                    chapter.getId() + ":mangaka");
        }
    }

    /**
     * Proposal đang ở trạng thái "revision" (tantou yêu cầu sửa) mà quá hạn
     * revisionDeadline vẫn chưa nộp lại thì tự động khoá (locked) — mangaka
     * không nộp lại kịp thì coi như bị từ chối vĩnh viễn.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void autoLockOverdueRevisionProposals() {
        LocalDateTime now = LocalDateTime.now();
        List<Proposal> overdueProposals = proposalRepository.findByStatusAndRevisionDeadlineBefore("revision", now);

        for (Proposal proposal : overdueProposals) {
            proposal.setStatus("locked");
            // Lưu lý do từ chối vào proposal để mangaka luôn xem lại được (không chỉ
            // nằm ở thông báo bị trôi). Giữ lại góp ý cũ của tantou nếu có.
            String overdueReason = "Quá hạn nộp lại sau khi được yêu cầu chỉnh sửa nên bị từ chối tự động.";
            String oldComment = proposal.getComment();
            if (oldComment != null && !oldComment.isBlank()
                    && !oldComment.contains(overdueReason)) {
                proposal.setComment(overdueReason + " (Góp ý trước đó: " + oldComment.trim() + ")");
            } else if (oldComment == null || oldComment.isBlank()) {
                proposal.setComment(overdueReason);
            }
            proposalRepository.save(proposal);

            if (proposal.getMangaka() != null && proposal.getMangaka().getUser() != null) {
                sendToUser(proposal.getMangaka().getUser().getId(),
                        "Đề xuất \"" + proposal.getSeriesName()
                                + "\" đã quá hạn nộp lại sau khi yêu cầu chỉnh sửa nên bị từ chối tự động.",
                        "/manga/mangaka/my-projects",
                        TYPE_GENERIC,
                        "proposal-auto-lock:" + proposal.getId());
            }
        }
    }

    /**
     * Chỉ về "untask" khi tất cả task của assistant đã được duyệt hết (không còn
     * submission intask hoặc done chờ duyệt). Trùng logic với PageController để
     * cả 2 luồng (mangaka duyệt / chapter quá hạn hủy task) đều nhất quán.
     */
    private void refreshAssistantStatus(Assistant assistant) {
        boolean hasUnapprovedTask = !submissionRepository
                .findByAssistant_IdAndStatus(assistant.getId(), "intask").isEmpty()
                || !submissionRepository.findByAssistant_IdAndStatus(assistant.getId(), "done").isEmpty();
        assistant.setStatus(hasUnapprovedTask ? "intask" : "untask");
        assistantRepository.save(assistant);
    }

    /**
     * Huỷ hàng loạt mọi task chưa duyệt xong (intask/done) thuộc 1 series khi
     * series đó bị dừng vĩnh viễn ("stopped") — dùng chung cho cả 2 trường hợp:
     * bảo vệ bị hội đồng bác bỏ, và tự động dừng do quá hạn 1 tuần không nộp bảo vệ.
     */
    @Transactional
    public void cancelUnapprovedTasksForStoppedSeries(Series series) {
        List<Submission> unapproved = submissionRepository
                .findByPageId_Chapter_Series_IdAndStatusIn(series.getId(), List.of("intask", "done"));

        for (Submission sub : unapproved) {
            sub.setStatus("cancelled");
            submissionRepository.save(sub);

            if (sub.getAssistant() != null) {
                refreshAssistantStatus(sub.getAssistant());
                if (sub.getAssistant().getUser() != null) {
                    sendToUser(sub.getAssistant().getUser().getId(),
                            "Task " + sub.getId() + " đã bị huỷ do series '" + series.getSeriesName()
                                    + "' đã bị dừng phát hành.",
                            "/manga/assistant",
                            TYPE_SERIES_AUTO_STOPPED,
                            sub.getId());
                }
            }
        }

        // Mọi chapter CHƯA published thì khoá luôn thành "stopped" — nếu để nguyên
        // "unfinish"/"finish"/"pass" thì job autoFinishOverdueChapters() (quét theo
        // deadline) vẫn thấy "unfinish" quá hạn và tự nộp lên tantou liên tục dù
        // series đã chết. Chapter "stopped" không khớp điều kiện "unfinish" của
        // job đó nữa nên sẽ không bao giờ bị tự nộp nữa.
        List<Chapter> chapters = chapterRepository.findBySeries(series);
        for (Chapter chapter : chapters) {
            if (!"published".equals(chapter.getStatus()) && !"stopped".equals(chapter.getStatus())) {
                chapter.setStatus("stopped");
                chapterRepository.save(chapter);
            }
        }
    }

    /**
     * Series đang "pending_cancel" (đã bị vote dừng) mà quá 1 tuần kể từ lúc đó
     * vẫn chưa có Tantou nào nộp hồ sơ bảo vệ thì tự động dừng vĩnh viễn —
     * coi như không ai lên tiếng bảo vệ nên chấp nhận huỷ.
     */
    @Transactional
    @Scheduled(fixedDelay = 3_600_000)
    public void autoCancelUndefendedSeries() {
        List<Series> pendingSeries = seriesRepository.findByStatus("pending_cancel");
        LocalDate today = LocalDate.now();

        for (Series series : pendingSeries) {
            // Tantou đã nộp hồ sơ bảo vệ rồi (dù đang chờ vote hay đã có kết quả) thì bỏ qua,
            // để luồng vote "defense" ở RankingController tự xử lý kết quả.
            if (voteSessionRepository.existsBySeriesIdAndVoteType(series.getId(), "defense")) {
                continue;
            }

            VoteSession stopSession = voteSessionRepository
                    .findFirstBySeriesIdAndVoteTypeAndStatusAndResultPassedTrueOrderByClosedAtDesc(
                            series.getId(), "stop", "closed")
                    .orElse(null);
            if (stopSession == null || stopSession.getClosedAt() == null) {
                continue;
            }

            if (!today.isAfter(stopSession.getClosedAt().plusWeeks(1))) {
                continue;
            }

            series.setStatus("stopped");
            seriesRepository.save(series);
            cancelUnapprovedTasksForStoppedSeries(series);

            if (series.getProposal() != null && series.getProposal().getMangaka() != null) {
                Mangaka mangaka = series.getProposal().getMangaka();
                String content = "⛔ Series '" + series.getSeriesName()
                        + "' đã bị dừng vĩnh viễn do quá 1 tuần không nộp hồ sơ bảo vệ.";
                if (mangaka.getUser() != null) {
                    sendToUser(mangaka.getUser().getId(), content, "/manga/mangaka",
                            TYPE_SERIES_AUTO_STOPPED, series.getId());
                }
                if (mangaka.getEditor() != null && mangaka.getEditor().getUser() != null) {
                    sendToUser(mangaka.getEditor().getUser().getId(), content, "/manga/tantou",
                            TYPE_SERIES_AUTO_STOPPED, series.getId() + ":tantou");
                }
            }
        }
    }

    @Transactional
    @Scheduled(fixedDelay = 3_600_000)
    public void scanDeadlineReminders() {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime soon = today.plusDays(1);

        remindSubmissions(
                submissionRepository.findByStatusAndDeadlineBetween("intask", today, soon),
                TYPE_DEADLINE_SOON,
                "Sắp đến hạn",
                today,
                false
        );

        remindSubmissions(
                submissionRepository.findByStatusAndDeadlineBefore("intask", today),
                TYPE_DEADLINE_OVERDUE,
                "Đã quá hạn",
                today,
                true
        );
    }

    private void remindSubmissions(List<Submission> submissions, String type, String label, LocalDateTime today,
            boolean overdue) {
        for (Submission submission : submissions) {
            Assistant assistant = submission.getAssistant();
            if (assistant == null || assistant.getUser() == null || submission.getPageId() == null
                    || submission.getPageId().getChapter() == null
                    || submission.getPageId().getChapter().getSeries() == null) {
                continue;
            }

            LocalDateTime deadline = submission.getDeadline();
            if (deadline == null) {
                continue;
            }

            String referenceKey = submission.getId() + ":" + today + ":" + type;
            String seriesName = submission.getPageId().getChapter().getSeries().getSeriesName();
            String chapterLabel = "Chapter " + submission.getPageId().getChapter().getChapterNumber()
                    + ", Page " + submission.getPageId().getPageNumber();
            String content = label + " cho " + seriesName + " - " + chapterLabel
                    + " (deadline " + deadline + ")";
            String link = "/manga/assistant/submission/" + submission.getId() + "/edit";

            sendToUser(assistant.getUser().getId(), content, link, type, referenceKey);
        }
    }
}
