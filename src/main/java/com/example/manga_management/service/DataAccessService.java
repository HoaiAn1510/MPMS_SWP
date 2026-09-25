package com.example.manga_management.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.VoteSessionRepository;

/**
 * Kiểm tra quyền theo DỮ LIỆU (chủ sở hữu / người được giao) — bổ sung cho
 * phân quyền theo vai trò trong SecurityConfig. Dùng chung cho các endpoint
 * {@code /api/**} và cho việc tải file nhạy cảm.
 */
@Service
public class DataAccessService {

    /** /proposal/PPS001.pdf, /series-defense/VS1P8X.pdf, /tantou-profile/PPS013.pdf */
    private static final Pattern SENSITIVE_FILE = Pattern
            .compile("^/(proposal|series-defense|tantou-profile)/([A-Za-z0-9_-]+)\\.[A-Za-z0-9]{1,10}$");

    /** /MangaPage/PG00001.png, /Submission/SUB001.png, /Submission/SUB001_v2_assigned.png */
    private static final Pattern PAGE_IMAGE = Pattern.compile("^/MangaPage/([A-Za-z0-9]+)\\.[A-Za-z0-9]{1,10}$");
    private static final Pattern SUBMISSION_IMAGE = Pattern
            .compile("^/Submission/([A-Za-z0-9]+)(?:_[A-Za-z0-9_]+)?\\.[A-Za-z0-9]{1,10}$");

    private final ProposalRepository proposalRepository;
    private final MangaPageRepository mangaPageRepository;
    private final SubmissionRepository submissionRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final AssistantRepository assistantRepository;

    public DataAccessService(ProposalRepository proposalRepository,
            VoteSessionRepository voteSessionRepository,
            AssistantRepository assistantRepository,
            MangaPageRepository mangaPageRepository,
            SubmissionRepository submissionRepository) {
        this.mangaPageRepository = mangaPageRepository;
        this.submissionRepository = submissionRepository;
        this.proposalRepository = proposalRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.assistantRepository = assistantRepository;
    }

    public boolean hasRole(User user, String role) {
        return user != null && user.getRole() != null && user.getRole().trim().equalsIgnoreCase(role);
    }

    /** User này là chính Mangaka đó (so theo user id). */
    public boolean isMangakaUser(Mangaka mangaka, User user) {
        return mangaka != null && user != null && mangaka.getUser() != null
                && user.getId() != null && user.getId().equals(mangaka.getUser().getId());
    }

    /** User này là Tantou đang phụ trách Mangaka đó. */
    public boolean isTantouOfMangaka(Mangaka mangaka, User user) {
        return mangaka != null && user != null && mangaka.getEditor() != null
                && mangaka.getEditor().getUser() != null
                && user.getId() != null && user.getId().equals(mangaka.getEditor().getUser().getId());
    }

    public boolean isOwnerMangaka(Series series, User user) {
        return series != null && series.getProposal() != null && isMangakaUser(series.getProposal().getMangaka(), user);
    }

    public boolean isTantouOfSeries(Series series, User user) {
        return series != null && series.getProposal() != null
                && isTantouOfMangaka(series.getProposal().getMangaka(), user);
    }

    /** Trợ lý thuộc về Mangaka sở hữu series này. */
    public boolean isAssistantOfSeries(Series series, User user) {
        if (series == null || series.getProposal() == null || series.getProposal().getMangaka() == null
                || user == null || user.getId() == null) {
            return false;
        }
        Optional<Assistant> assistant = assistantRepository.findByUserId(user.getId());
        return assistant.isPresent() && assistant.get().getMangaka() != null
                && series.getProposal().getMangaka().getId() != null
                && series.getProposal().getMangaka().getId().equals(assistant.get().getMangaka().getId());
    }

    /** Xem thông tin series: admin, chủ series, tantou phụ trách, hội đồng. */
    public boolean canViewSeriesInfo(Series series, User user) {
        return hasRole(user, "ADMIN") || hasRole(user, "BOARD")
                || isOwnerMangaka(series, user) || isTantouOfSeries(series, user);
    }

    /** Xem nội dung sản xuất (chapter/trang) của series: admin, chủ series, tantou, trợ lý của chủ series. */
    public boolean canViewSeriesContent(Series series, User user) {
        return hasRole(user, "ADMIN") || isOwnerMangaka(series, user) || isTantouOfSeries(series, user)
                || isAssistantOfSeries(series, user);
    }

    /** Xem/sửa dữ liệu vận hành của series: chủ series hoặc tantou phụ trách (không tính admin). */
    public boolean isSeriesStaff(Series series, User user) {
        return isOwnerMangaka(series, user) || isTantouOfSeries(series, user);
    }

    /**
     * Người dùng có được tải file nhạy cảm {@code requestPath} (đã decode, vd
     * {@code /proposal/PPS001.pdf}) hay không. Đường dẫn lạ → từ chối.
     */
    @Transactional(readOnly = true)
    public boolean canAccessSensitiveFile(User user, String requestPath) {
        if (user == null || requestPath == null) {
            return false;
        }
        Matcher m = SENSITIVE_FILE.matcher(requestPath);
        if (!m.matches()) {
            return false;
        }
        if (hasRole(user, "ADMIN")) {
            return true;
        }
        String dir = m.group(1);
        String id = m.group(2);

        if ("series-defense".equals(dir)) {
            VoteSession session = voteSessionRepository.findById(id).orElse(null);
            if (session == null) {
                return false;
            }
            // Hồ sơ bảo vệ: hội đồng (bỏ phiếu), tantou phụ trách, chủ series.
            return hasRole(user, "BOARD") || isSeriesStaff(session.getSeries(), user);
        }

        Proposal proposal = proposalRepository.findById(id).orElse(null);
        if (proposal == null) {
            return false;
        }
        boolean boardVisible = hasRole(user, "BOARD")
                && ("board_check".equals(proposal.getStatus()) || proposal.getBoardReviewedAt() != null);
        if ("tantou-profile".equals(dir)) {
            // Hồ sơ tantou nộp hội đồng: tantou phụ trách + hội đồng, KHÔNG gồm tác giả.
            return boardVisible || isTantouOfMangaka(proposal.getMangaka(), user);
        }
        // Bản thảo đề xuất: tác giả, tantou phụ trách, hội đồng (khi đã lên bàn hội đồng).
        return boardVisible || isMangakaUser(proposal.getMangaka(), user)
                || isTantouOfMangaka(proposal.getMangaka(), user);
    }

    /**
     * Ảnh trang / bài nộp ({@code /MangaPage/**}, {@code /Submission/**}) là bản thảo chưa
     * xuất bản: chỉ admin, chủ series, tantou phụ trách và trợ lý được giao trang đó xem được.
     */
    @Transactional(readOnly = true)
    public boolean canAccessProductionImage(User user, String requestPath) {
        if (user == null || requestPath == null) {
            return false;
        }
        MangaPage page;
        Matcher pageMatch = PAGE_IMAGE.matcher(requestPath);
        Matcher submissionMatch = SUBMISSION_IMAGE.matcher(requestPath);
        if (pageMatch.matches()) {
            page = mangaPageRepository.findById(pageMatch.group(1)).orElse(null);
        } else if (submissionMatch.matches()) {
            Submission submission = submissionRepository.findById(submissionMatch.group(1)).orElse(null);
            if (submission == null) {
                return hasRole(user, "ADMIN");
            }
            if (submission.getAssistant() != null && submission.getAssistant().getUser() != null
                    && user.getId() != null && user.getId().equals(submission.getAssistant().getUser().getId())) {
                return true;
            }
            page = submission.getPageId();
        } else {
            return false;
        }
        if (page == null || page.getChapter() == null) {
            return hasRole(user, "ADMIN");
        }
        Series series = page.getChapter().getSeries();
        if (hasRole(user, "ADMIN") || isSeriesStaff(series, user)) {
            return true;
        }
        // Trợ lý từng được giao trang này (kể cả các vòng trước).
        return submissionRepository.findByPageIdId(page.getId()).stream()
                .anyMatch(s -> s.getAssistant() != null && s.getAssistant().getUser() != null
                        && user.getId() != null && user.getId().equals(s.getAssistant().getUser().getId()));
    }
}
