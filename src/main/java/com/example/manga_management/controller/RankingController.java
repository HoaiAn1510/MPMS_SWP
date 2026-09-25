package com.example.manga_management.controller;

import java.time.LocalDate;
import java.util.*;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.example.manga_management.entity.*;
import com.example.manga_management.repository.*;
import com.example.manga_management.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/manga/ranking")
@Tag(name = "Ranking", description = "Bảng xếp hạng series và bỏ phiếu hội đồng (stop/defense/reward)")
public class RankingController {

    private final RankingRepository rankingRepository;
    private final BoardRepository boardRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SeriesRepository seriesRepository;
    private final LikeResultRepository likeResultRepository;
    private final ChapterRepository chapterRepository;
    private final NotificationController notificationController;
    private final NotificationService notificationService;

    public RankingController(RankingRepository rankingRepository,
            BoardRepository boardRepository,
            VoteSessionRepository voteSessionRepository,
            SeriesVoteRepository seriesVoteRepository,
            SeriesRepository seriesRepository,
            LikeResultRepository likeResultRepository,
            ChapterRepository chapterRepository,
            NotificationController notificationController,
            NotificationService notificationService) {
        this.rankingRepository = rankingRepository;
        this.boardRepository = boardRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.seriesVoteRepository = seriesVoteRepository;
        this.seriesRepository = seriesRepository;
        this.likeResultRepository = likeResultRepository;
        this.chapterRepository = chapterRepository;
        this.notificationController = notificationController;
        this.notificationService = notificationService;
    }

    // ===== 1. API ranking =====
    @Operation(summary = "Bảng xếp hạng series theo tháng/quý/năm (view, like, dislike)")
    @GetMapping
    public List<Map<String, Object>> getRanking(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int quarter,
            @RequestParam(defaultValue = "2026") int year) {

        List<Object[]> rows;
        if (quarter >= 1 && quarter <= 4) {
            rows = rankingRepository.findRankingByQuarterAndYear(getMonthsForQuarter(quarter), year);
        } else if (month > 0) {
            rows = rankingRepository.findRankingByMonthAndYear(month, year);
        } else {
            rows = rankingRepository.findRankingByYear(year);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", i + 1);
            map.put("seriesId", row[0]);
            map.put("seriesName", row[1]);
            map.put("totalView", row[2]);
            map.put("totalLike", row[3]);
            map.put("totalDislike", row[4]);
            result.add(map);
        }
        return result;
    }

    // ===== 2. Lấy danh sách phiên vote đang mở (tất cả board thấy) =====
    @Operation(summary = "Danh sách phiên vote (stop/defense/reward) và tiến độ bỏ phiếu")
    @GetMapping("/active-sessions")
    public List<Map<String, Object>> getActiveSessions(HttpSession session) {
        User user = (User) session.getAttribute("user");
        String boardId = null;
        if (user != null) {
            boardId = boardRepository.findByUser_Id(user.getId())
                    .map(Board::getId).orElse(null);
        }

        long totalBoards = boardRepository.count();
        // Lấy tất cả phiên (do Admin tạo) - cả active và closed
        List<VoteSession> sessions = voteSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> result = new ArrayList<>();

        for (VoteSession vs : sessions) {
            String positiveChoice = switch (vs.getVoteType()) {
                case "stop" -> "stop";
                case "defense" -> "approve";
                default -> "reward";
            };

            long voted = rankingRepository.countSeriesVoteBySession(vs.getId());
            long positive = rankingRepository.countSeriesVoteByChoiceAndSession(vs.getId(), positiveChoice);
            boolean alreadyVoted = boardId != null &&
                    rankingRepository.countSeriesVoteByBoardAndSession(vs.getId(), boardId) > 0;
            LocalDate votedAt = boardId == null ? null
                    : seriesVoteRepository.findFirstBySession_IdAndBoard_Id(vs.getId(), boardId)
                            .map(SeriesVote::getVoteDate)
                            .orElse(null);

            double percent = (totalBoards > 0 && voted >= totalBoards)
                    ? (positive * 100.0 / totalBoards) : 0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sessionId", vs.getId());
            map.put("seriesId", vs.getSeries().getId());
            map.put("seriesName", vs.getSeries().getSeriesName());
            map.put("voteType", vs.getVoteType());
            map.put("sessionStatus", vs.getStatus());
            map.put("createdAt", vs.getCreatedAt().toString());
            map.put("closedAt", vs.getClosedAt());
            map.put("votedAt", votedAt);
            map.put("autoCreated", vs.isAutoCreated());
            map.put("createdBy", vs.getCreatedBy() != null
                    ? vs.getCreatedBy().getUser().getFullname()
                    : "Quản trị viên");
            map.put("voted", voted);
            map.put("totalBoards", totalBoards);
            map.put("positiveVotes", positive);
            map.put("percent", Math.round(percent));
            map.put("alreadyVoted", alreadyVoted);
            map.put("reason", vs.getReason());
            map.put("defenseFilePath", vs.getDefenseFilePath());
            map.put("defenseNote", vs.getDefenseNote());
            boolean isCreator = !vs.isAutoCreated() && boardId != null
                    && vs.getCreatedBy() != null
                    && vs.getCreatedBy().getId().equals(boardId);
            map.put("isCreator", isCreator);
            result.add(map);
        }
        return result;
    }

    // ===== 5. Board cast vote vào phiên =====
    @Operation(summary = "Board bỏ phiếu cho một phiên vote series đang mở")
    @PostMapping("/cast-session-vote")
    public Map<String, Object> castSessionVote(
            @RequestParam String sessionId,
            @RequestParam String voteChoice,
            @RequestParam(required = false) String content,
            HttpSession session) {

        Map<String, Object> response = new LinkedHashMap<>();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.put("success", false);
            response.put("message", "Bạn chưa đăng nhập!");
            return response;
        }

        Optional<Board> boardOpt = boardRepository.findByUser_Id(user.getId());
        if (boardOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Bạn không phải thành viên Editorial Board!");
            return response;
        }
        Board board = boardOpt.get();

        Optional<VoteSession> sessionOpt = voteSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy phiên vote!");
            return response;
        }
        VoteSession vs = sessionOpt.get();

        if (!vs.getStatus().equals("active")) {
            response.put("success", false);
            response.put("message", "Phiên vote này đã đóng!");
            return response;
        }

        // Validate lựa chọn theo loại vote
        if (vs.getVoteType().equals("stop")) {
            if (!voteChoice.equals("stop") && !voteChoice.equals("keep")) {
                response.put("success", false);
                response.put("message", "Lựa chọn không hợp lệ (stop hoặc keep)!");
                return response;
            }
        } else if (vs.getVoteType().equals("defense")) {
            if (!voteChoice.equals("approve") && !voteChoice.equals("reject")) {
                response.put("success", false);
                response.put("message", "Lựa chọn không hợp lệ (approve hoặc reject)!");
                return response;
            }
        } else {
            if (!voteChoice.equals("reward") && !voteChoice.equals("against")) {
                response.put("success", false);
                response.put("message", "Lựa chọn không hợp lệ (reward hoặc against)!");
                return response;
            }
        }

        if (rankingRepository.countSeriesVoteByBoardAndSession(vs.getId(), board.getId()) > 0) {
            response.put("success", false);
            response.put("message", "Bạn đã vote trong phiên này rồi!");
            return response;
        }

        // Đếm trước khi save để tránh lỗi JPA flush timing
        long totalBoards = boardRepository.count();
        String positiveChoice = switch (vs.getVoteType()) {
            case "stop" -> "stop";
            case "defense" -> "approve";
            default -> "reward";
        };
        long votedBefore = rankingRepository.countSeriesVoteBySession(vs.getId());
        long positiveBefore = rankingRepository.countSeriesVoteByChoiceAndSession(vs.getId(), positiveChoice);

        SeriesVote sv = new SeriesVote();
        sv.setID(generateSvoteId());
        sv.setSeries(vs.getSeries());
        sv.setBoard(board);
        sv.setSession(vs);
        sv.setVote(voteChoice);
        sv.setVoteDate(LocalDate.now());
        sv.setContent(content);
        try {
            // save + flush ngay để ràng buộc unique (SessionID, BoardID) ở DB bắt
            // được 2 request đồng thời cùng vượt qua check countSeriesVoteByBoardAndSession
            // ở trên (race condition) — chỉ 1 trong 2 được ghi.
            seriesVoteRepository.saveAndFlush(sv);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            response.put("success", false);
            response.put("message", "Bạn đã vote trong phiên này rồi!");
            return response;
        }

        long voted = votedBefore + 1;
        long positive = positiveBefore + (voteChoice.equals(positiveChoice) ? 1 : 0);

        response.put("success", true);

        if (voted < totalBoards) {
            response.put("message", "Đã ghi nhận vote! Còn " + (totalBoards - voted) + " board chưa vote.");
            return response;
        }

        // Đủ tất cả board → tính kết quả
        double percent = totalBoards > 0 ? (positive * 100.0 / totalBoards) : 0;
        response.put("percent", Math.round(percent));

        vs.setStatus("closed");
        vs.setClosedAt(LocalDate.now());
        vs.setResultPassed(percent >= 60);

        Series series = vs.getSeries();
        if (vs.getVoteType().equals("stop")) {
            if (percent >= 60) {
                series.setStatus("pending_cancel");
                seriesRepository.save(series);
                notifySeriesStakeholders(series,
                        "⚠️ Series '" + series.getSeriesName() + "' đã bị vote dừng (" + Math.round(percent)
                                + "% đồng ý). Vui lòng nộp hồ sơ bảo vệ nếu muốn tiếp tục.",
                        "/manga/mangaka");
                response.put("message", "⚠️ Tất cả board đã vote. Series chuyển sang chờ hồ sơ bảo vệ! ("
                        + Math.round(percent) + "% đồng ý dừng)");
            } else {
                response.put("message", "Tất cả board đã vote. Kết quả: " + Math.round(percent) + "% vote dừng → Series tiếp tục.");
            }
        } else if (vs.getVoteType().equals("defense")) {
            if (percent >= 60) {
                // Trả về đúng trạng thái đang hoạt động: "published" nếu series đã
                // từng xuất bản chapter, ngược lại "unfinish". (Trước Nhóm A series
                // chỉ có "unfinish" nên đặt cứng — nay có thêm "published".)
                boolean hasPublished = !chapterRepository
                        .findBySeries_IdAndStatus(series.getId(), "published").isEmpty();
                series.setStatus(hasPublished ? "published" : "unfinish");
                seriesRepository.save(series);
                notifySeriesStakeholders(series,
                        "✅ Hồ sơ bảo vệ của series '" + series.getSeriesName()
                                + "' đã được hội đồng thông qua (" + Math.round(percent)
                                + "% đồng ý). Series được tiếp tục hoạt động bình thường.",
                        "/manga/mangaka");
                response.put("message", "✅ Hồ sơ bảo vệ được thông qua! Series tiếp tục hoạt động.");
            } else {
                series.setStatus("stopped");
                seriesRepository.save(series);
                notificationService.cancelUnapprovedTasksForStoppedSeries(series);
                notifySeriesStakeholders(series,
                        "❌ Hồ sơ bảo vệ của series '" + series.getSeriesName()
                                + "' không được hội đồng thông qua (" + Math.round(percent)
                                + "% đồng ý). Series bị DỪNG.",
                        "/manga/mangaka");
                response.put("message", "❌ Hồ sơ bảo vệ không đủ phiếu đồng ý. Series bị dừng.");
            }
        } else {
            if (percent >= 60) {
                // Không đổi status của series — thưởng chỉ đánh dấu ở từng chapter
                // (isReward), series vẫn tiếp tục hoạt động bình thường để có thể
                // được xét thưởng tiếp cho các chapter mới sau này.
                int bonus = computeAndLockRewardBonus(series, vs);
                response.put("message", "🏆 Tất cả board đã vote. Series được KHEN THƯỞNG! (" + Math.round(percent)
                        + "% đồng ý, thưởng " + bonus + ")");
            } else {
                response.put("message", "Tất cả board đã vote. Kết quả: " + Math.round(percent) + "% vote khen thưởng → Không đủ 60%.");
            }
        }
        voteSessionRepository.save(vs);
        return response;
    }

    /**
     * Tính thưởng 1 lần cho series vừa được vote thưởng thông qua: 10% x
     * salaryPerChapter x số chapter "published" CHƯA từng được thưởng trước đó.
     * Đánh dấu ngay các chapter đó isReward=true để không bị tính lại ở lần
     * thưởng sau (kể cả cùng tháng hay tháng khác), và chốt số tiền vào chính
     * phiên vote này để tra lại sau không bị lệch do chapter mới publish thêm.
     */
    private int computeAndLockRewardBonus(Series series, VoteSession vs) {
        if (series.getProposal() == null || series.getProposal().getMangaka() == null) {
            vs.setRewardBonusAmount(0);
            return 0;
        }
        Mangaka mangaka = series.getProposal().getMangaka();
        List<Chapter> publishedChapters = chapterRepository.findBySeries_IdAndStatus(series.getId(), "published");
        List<Chapter> eligibleChapters = publishedChapters.stream().filter(c -> !c.isReward()).toList();

        int bonus = (int) Math.round(0.10 * mangaka.getSalaryPerChapter() * eligibleChapters.size());

        for (Chapter c : eligibleChapters) {
            c.setReward(true);
        }
        chapterRepository.saveAll(eligibleChapters);

        vs.setRewardBonusAmount(bonus);
        return bonus;
    }

    /**
     * Báo cho cả mangaka sở hữu series và tantou phụ trách khi có kết quả vote
     * ảnh hưởng tới trạng thái series (dừng chờ bảo vệ, hồ sơ bảo vệ đậu/rớt).
     */
    private void notifySeriesStakeholders(Series series, String content, String mangakaLink) {
        if (series.getProposal() == null || series.getProposal().getMangaka() == null) {
            return;
        }
        Mangaka mangaka = series.getProposal().getMangaka();
        if (mangaka.getUser() != null) {
            notificationController.send(null, mangaka.getUser().getId(), content, mangakaLink);
        }
        if (mangaka.getEditor() != null && mangaka.getEditor().getUser() != null) {
            notificationController.send(null, mangaka.getEditor().getUser().getId(), content, "/manga/tantou");
        }
    }

    private List<Integer> getMonthsForQuarter(int quarter) {
        int start = (quarter - 1) * 3 + 1;
        return List.of(start, start + 1, start + 2);
    }

    private String generateSvoteId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("SV");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }
}
