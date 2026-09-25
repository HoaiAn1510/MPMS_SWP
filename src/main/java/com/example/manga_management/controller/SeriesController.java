package com.example.manga_management.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.LikeResult;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.LikeResultRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SeriesVoteRepository;
import com.example.manga_management.repository.VoteSessionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/series")
@Tag(name = "Series", description = "Series Management APIs")
public class SeriesController {

    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private SeriesVoteRepository seriesVoteRepository;
    @Autowired
    private LikeResultRepository likeResultRepository;
    @Autowired
    private VoteSessionRepository voteSessionRepository;
    @Autowired
    private BoardRepository boardRepository;

    /** Series này có thuộc đúng Mangaka đang đăng nhập không. */
    private boolean isOwnSeries(Series series, User user) {
        return series != null && user != null && series.getProposal() != null
                && series.getProposal().getMangaka() != null
                && series.getProposal().getMangaka().getUser() != null
                && series.getProposal().getMangaka().getUser().getId().equals(user.getId());
    }

    @GetMapping
    @Operation(summary = "[SWAGGER] Lấy danh sách tất cả series")
    @ResponseBody
    public Map<String, Object> getAllSeries() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Series> series = seriesRepository.findAll();
            result.put("status", "success");
            result.put("series", series);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/mangaka/{mangakaId}")
    @Operation(summary = "[SWAGGER] Lấy danh sách series theo Mangaka ID")
    @ResponseBody
    public Map<String, Object> getSeriesByMangakaId(@PathVariable String mangakaId) {
        Map<String, Object> result = new HashMap<>();

        List<Series> seriesList = seriesRepository.findByProposal_Mangaka_Id(mangakaId);
        if (seriesList.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series nào của Mangaka: " + mangakaId);
            return result;
        }

        result.put("status", "success");
        result.put("mangakaId", mangakaId);
        result.put("series", seriesList);
        return result;
    }

    @GetMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Lấy thông tin chi tiết một series")
    @ResponseBody
    public Map<String, Object> getSeriesById(@PathVariable String seriesId) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        result.put("status", "success");
        result.put("series", series);
        return result;
    }

    @GetMapping("/{seriesId}/info")
    @Operation(summary = "Xem toàn bộ thông tin series: proposal, thể loại, vote, view, số chapter")
    @ResponseBody
    public Map<String, Object> getSeriesInfo(@PathVariable String seriesId) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        Proposal proposal = series.getProposal();
        Map<String, Object> proposalInfo = new HashMap<>();
        if (proposal != null) {
            proposalInfo.put("id", proposal.getId());
            proposalInfo.put("genre", proposal.getGenre());
            proposalInfo.put("editorScore", proposal.getEditorScore());
            proposalInfo.put("comment", proposal.getComment());
            proposalInfo.put("createdAt", proposal.getCreatedAt());
            proposalInfo.put("submittedAt",
                    proposal.getSubmittedAt() != null ? proposal.getSubmittedAt() : proposal.getCreatedAt());
            proposalInfo.put("reviewedAt", proposal.getReviewedAt());
            proposalInfo.put("boardSubmittedAt", proposal.getBoardSubmittedAt());
            proposalInfo.put("boardReviewedAt", proposal.getBoardReviewedAt());
            proposalInfo.put("mangakaName",
                    proposal.getMangaka() != null && proposal.getMangaka().getUser() != null
                            ? proposal.getMangaka().getUser().getFullname()
                            : null);
        }

        List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
        Map<String, Long> chapterStats = new HashMap<>();
        chapterStats.put("total", (long) chapters.size());
        chapterStats.put("unfinish", chapters.stream().filter(c -> "unfinish".equals(c.getStatus())).count());
        chapterStats.put("waitingReview", chapters.stream().filter(c -> "finish".equals(c.getStatus())).count());
        chapterStats.put("pass", chapters.stream().filter(c -> "pass".equals(c.getStatus())).count());
        chapterStats.put("published", chapters.stream().filter(c -> "published".equals(c.getStatus())).count());

        long totalVotes = seriesVoteRepository.countBySeries_Id(seriesId);
        // Lượt xem / like / dislike đều lấy từ LikeResult (cộng dồn mọi tháng/năm)
        List<LikeResult> likeResults = likeResultRepository.findBySeries_Id(seriesId);
        int totalViews = likeResults.stream().mapToInt(LikeResult::getViewCount).sum();
        int totalLikes = likeResults.stream().mapToInt(LikeResult::getLikeNumber).sum();
        int totalDislikes = likeResults.stream().mapToInt(LikeResult::getDislikeNumber).sum();

        // Lịch sử các phiên vote (stop/reward/defense) — hiện lý do + hồ sơ bảo vệ +
        // kết quả (số phiếu đồng ý / tổng board) cho phiên đã đóng
        long totalBoards = boardRepository.count();
        List<Map<String, Object>> voteSessions = voteSessionRepository
                .findBySeriesIdOrderByCreatedAtDesc(seriesId)
                .stream()
                .map(vs -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("sessionId", vs.getId());
                    map.put("voteType", vs.getVoteType());
                    map.put("status", vs.getStatus());
                    map.put("createdAt", vs.getCreatedAt());
                    map.put("closedAt", vs.getClosedAt());
                    map.put("reason", vs.getReason());
                    map.put("defenseFilePath", vs.getDefenseFilePath());
                    map.put("defenseNote", vs.getDefenseNote());
                    map.put("votes", seriesVoteRepository.findBySeries_IdOrderByVoteDateDesc(seriesId)
                            .stream()
                            .filter(vote -> vote.getSession() != null && vs.getId().equals(vote.getSession().getId()))
                            .map(vote -> Map.<String, Object>of(
                                    "boardName", vote.getBoard() != null && vote.getBoard().getUser() != null
                                            ? vote.getBoard().getUser().getFullname()
                                            : "Hội đồng",
                                    "choice", vote.getVote(),
                                    "votedAt", vote.getVoteDate()))
                            .toList());

                    if ("closed".equals(vs.getStatus())) {
                        String positiveChoice = switch (vs.getVoteType()) {
                            case "stop" -> "stop";
                            case "defense" -> "approve";
                            default -> "reward";
                        };
                        long votedInSession = seriesVoteRepository.countBySession_Id(vs.getId());
                        long positiveInSession = seriesVoteRepository.countBySession_IdAndVote(vs.getId(), positiveChoice);
                        long percent = totalBoards > 0 ? Math.round(positiveInSession * 100.0 / totalBoards) : 0;
                        map.put("votedCount", votedInSession);
                        map.put("totalBoards", totalBoards);
                        map.put("positivePercent", percent);
                        map.put("passed", percent >= 60);
                    }
                    return map;
                })
                .toList();

        Map<String, Object> seriesInfo = new HashMap<>();
        seriesInfo.put("id", series.getId());
        seriesInfo.put("seriesName", series.getSeriesName());
        seriesInfo.put("description", series.getDescription());
        seriesInfo.put("genre", series.getGenre());
        seriesInfo.put("status", series.getStatus());
        seriesInfo.put("startDate", series.getStartDate());
        seriesInfo.put("bookJacket", series.getBookJacket());

        result.put("status", "success");
        result.put("series", seriesInfo);
        result.put("proposal", proposalInfo);
        result.put("chapterStats", chapterStats);
        result.put("totalViews", totalViews);
        result.put("totalVotes", totalVotes);
        result.put("totalLikes", totalLikes);
        result.put("totalDislikes", totalDislikes);
        result.put("voteSessions", voteSessions);
        return result;
    }

    @PostMapping("/create")
    @Operation(summary = "[SWAGGER] Tạo series mới")
    @ResponseBody
    public Map<String, Object> createSeries(@RequestParam String seriesName,
            @RequestParam String description, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        if (session.getAttribute("user") == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }

        try {
            long count = seriesRepository.count();
            String seriesId = String.format("SER%03d", count + 1);

            Series series = new Series();
            series.setId(seriesId);
            series.setSeriesName(seriesName);
            series.setDescription(description);
            series.setStartDate(LocalDate.now());
            series.setStatus("unfinish");
            seriesRepository.save(series);

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Tạo series thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @PutMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Cập nhật thông tin series")
    @ResponseBody
    public Map<String, Object> updateSeries(@PathVariable String seriesId,
            @RequestParam String seriesName,
            @RequestParam String description, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        User user = (User) session.getAttribute("user");
        if (!isOwnSeries(series, user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên series này!");
            return result;
        }

        try {
            series.setSeriesName(seriesName);
            series.setDescription(description);
            seriesRepository.save(series);

            result.put("status", "success");
            result.put("seriesId", seriesId);
            result.put("message", "Cập nhật series thành công!");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/{seriesId}")
    @Operation(summary = "[SWAGGER] Xóa một series")
    @ResponseBody
    public Map<String, String> deleteSeries(@PathVariable String seriesId, HttpSession session) {
        Map<String, String> result = new HashMap<>();

        Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy series: " + seriesId);
            return result;
        }

        User user = (User) session.getAttribute("user");
        if (!isOwnSeries(series, user)) {
            result.put("status", "error");
            result.put("message", "Bạn không có quyền thao tác trên series này!");
            return result;
        }

        seriesRepository.deleteById(seriesId);
        result.put("status", "success");
        result.put("message", "Đã xóa series: " + seriesId);
        return result;
    }

    // @GetMapping("/{seriesId}/chapters")
    // @Operation(summary = "[SWAGGER] Lấy danh sách chapter của một series")
    // @ResponseBody
    // public Map<String, Object> getChaptersBySeriesId(@PathVariable String seriesId) {
    //     Map<String, Object> result = new HashMap<>();
    //     Series series = seriesRepository.findById(seriesId).orElse(null);
    //     if (series == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy series: " + seriesId);
    //         return result;
    //     }
    //     List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
    //     result.put("status", "success");
    //     result.put("seriesId", seriesId);
    //     result.put("chapters", chapters);
    //     return result;
    // }
    // @PostMapping("/{seriesId}/chapters")
    // @Operation(summary = "[SWAGGER] Tạo chapter mới trong một series")
    // @ResponseBody
    // public Map<String, Object> createChapter(@PathVariable String seriesId,
    //         @RequestParam String chapterName) {
    //     Map<String, Object> result = new HashMap<>();
    //     Series series = seriesRepository.findById(seriesId).orElse(null);
    //     if (series == null) {
    //         result.put("status", "error");
    //         result.put("message", "Không tìm thấy series: " + seriesId);
    //         return result;
    //     }
    //     try {
    //         Optional<Chapter> lastChapter = chapterRepository.findTopByOrderByIdDesc();
    //         int maxId = 0;
    //         if (lastChapter.isPresent()) {
    //             maxId = Integer.parseInt(lastChapter.get().getId().substring(3));
    //         }
    //         String newId = "CHP" + String.format("%03d", maxId + 1);
    //         List<Chapter> chapters = chapterRepository.findBySeriesId(seriesId);
    //         int nextChapterNumber = chapters.size() + 1;
    //         Chapter chapter = new Chapter();
    //         chapter.setId(newId);
    //         chapter.setSeries(series);
    //         chapter.setChapterName(chapterName);
    //         chapter.setChapterNumber(nextChapterNumber);
    //         chapter.setDeadline(resolveNextSaturday(series));
    //         chapter.setStatus("unfinish");
    //         chapterRepository.save(chapter);
    //         result.put("status", "success");
    //         result.put("chapterId", newId);
    //         result.put("chapterNumber", nextChapterNumber);
    //         result.put("message", "Tạo chapter thành công!");
    //     } catch (Exception e) {
    //         result.put("status", "error");
    //         result.put("message", "Lỗi hệ thống: " + e.getMessage());
    //     }
    //     return result;
    // }
}
