package com.example.manga_management.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.BoardProposalCommentRepository;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.service.ProposalService;
import com.example.manga_management.service.EditorialAiService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/manga/editor")
public class BoardController {

    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final NotificationController notificationController;
    private final BoardRepository boardRepository;
    private final BoardProposalCommentRepository boardProposalCommentRepository;
    private final EditorialAiService editorialAiService;

    public BoardController(ProposalRepository proposalRepository, ProposalService proposalService,
            NotificationController notificationController, BoardRepository boardRepository,
            BoardProposalCommentRepository boardProposalCommentRepository,
            EditorialAiService editorialAiService) {
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.notificationController = notificationController;
        this.boardRepository = boardRepository;
        this.boardProposalCommentRepository = boardProposalCommentRepository;
        this.editorialAiService = editorialAiService;
    }

    @GetMapping("")
    public String editorPage(HttpSession session, Model model) {
        Object userObj = session.getAttribute("user");
        if (!(userObj instanceof User user))
            return "redirect:/login";
        model.addAttribute("currentUserId", user.getId());
        return "editor";
    }

    @PostMapping("/ai/brief")
    @ResponseBody
    public Map<String, Object> aiBrief(
            @RequestParam String proposalId,
            @RequestParam(required = false) String draft,
            HttpSession session) {
        return runEditorialAi("board_brief", proposalId, draft, session);
    }

    @PostMapping("/ai/decision-summary")
    @ResponseBody
    public Map<String, Object> aiDecisionSummary(
            @RequestParam String proposalId,
            @RequestParam(required = false) String draft,
            HttpSession session) {
        return runEditorialAi("decision_summary", proposalId, draft, session);
    }

    private Map<String, Object> runEditorialAi(
            String mode, String proposalId, String draft, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Object userObj = session.getAttribute("user");
        if (!(userObj instanceof User user)) {
            result.put("status", "error");
            result.put("message", "Ch\u01b0a \u0111\u0103ng nh\u1eadp!");
            return result;
        }
        if (boardRepository.findByUser_Id(user.getId()).isEmpty()) {
            result.put("status", "error");
            result.put("message", "B\u1ea1n kh\u00f4ng ph\u1ea3i th\u00e0nh vi\u00ean h\u1ed9i \u0111\u1ed3ng.");
            return result;
        }
        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null || !"board_check".equals(proposal.getStatus())) {
            result.put("status", "error");
            result.put("message", "\u0110\u1ec1 xu\u1ea5t kh\u00f4ng c\u00f2n trong danh s\u00e1ch ch\u1edd h\u1ed9i \u0111\u1ed3ng.");
            return result;
        }

        try {
            return editorialAiService.assist("board", mode, proposalId, draft);
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

    @Operation(summary = "Danh sách đề xuất chờ hội đồng bỏ phiếu (kèm 2 file)")
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> boardData(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập!");
            return result;
        }

        User user = (User) userObj;
        Board board = boardRepository.findByUser_Id(user.getId()).orElse(null);
        List<Proposal> pending = proposalRepository.findByStatus("board_check");
        // Đề xuất đã có kết quả cuối cùng (passed/locked) — không bị xoá, chỉ
        // không còn ở trạng thái "board_check" nữa. Dùng boardReviewedAt để
        // nhận diện đúng những đề xuất hội đồng ĐÃ từng bỏ phiếu, kể cả khi
        // sau đó chuyển tiếp trạng thái (vd. "started").
        List<Proposal> history = proposalRepository.findByBoardReviewedAtIsNotNullOrderByBoardReviewedAtDesc();

        result.put("status", "success");
        result.put("userId", user.getId());
        result.put("boardId", board != null ? board.getId() : null);
        result.put("totalBoards", boardRepository.count());
        result.put("proposals", buildProposalPayload(pending, board));
        result.put("proposalsHistory", buildProposalPayload(history, board));
        result.put("total", pending.size());
        return result;
    }

    private List<Map<String, Object>> buildProposalPayload(List<Proposal> list, Board board) {
        List<Map<String, Object>> proposalPayload = new ArrayList<>();
        for (Proposal proposal : list) {
            Map<String, Object> proposalData = new HashMap<>();
            proposalData.put("id", proposal.getId());
            proposalData.put("seriesName", proposal.getSeriesName());
            proposalData.put("genre", proposal.getGenre());
            // Hội đồng phải thấy phạm vi tác giả cam kết TRƯỚC khi bỏ phiếu — sau
            // khi duyệt con số này thành trần cứng, không sửa được nữa.
            proposalData.put("plannedChapterCount", proposal.getPlannedChapterCount());
            proposalData.put("filePath", proposal.getFilePath());
            proposalData.put("fileOfTantou", proposal.getFileOfTantou());
            proposalData.put("status", proposal.getStatus());
            proposalData.put("voteCount", boardProposalCommentRepository.countByProposal_Id(proposal.getId()));
            proposalData.put("submittedAt",
                    proposal.getSubmittedAt() != null ? proposal.getSubmittedAt() : proposal.getCreatedAt());
            proposalData.put("reviewedAt", proposal.getReviewedAt());
            proposalData.put("boardSubmittedAt", proposal.getBoardSubmittedAt());
            proposalData.put("boardReviewedAt", proposal.getBoardReviewedAt());

            // Mục 6: nếu board hiện tại đã bỏ phiếu cho đề xuất này thì trả lại phiếu
            // của họ để FE hiển thị (thay vì hiện lại nút vote).
            if (board != null) {
                boardProposalCommentRepository
                        .findByProposal_IdAndBoard_Id(proposal.getId(), board.getId())
                        .ifPresent(myComment -> {
                            proposalData.put("myVote", myComment.getAction());
                            proposalData.put("myVoteContent", myComment.getContent());
                            proposalData.put("myVoteAt", myComment.getCreatedAt());
                        });
            }

            Map<String, Object> mangakaData = new HashMap<>();
            Map<String, Object> userData = new HashMap<>();
            if (proposal.getMangaka() != null && proposal.getMangaka().getUser() != null) {
                userData.put("fullname", proposal.getMangaka().getUser().getFullname());
                mangakaData.put("user", userData);
            }
            proposalData.put("mangaka", mangakaData);

            proposalPayload.add(proposalData);
        }
        return proposalPayload;
    }

    @Operation(summary = "Bỏ phiếu + comment cho đề xuất")
    @PostMapping("/vote")
    @ResponseBody
    public Map<String, String> boardReview(
            @RequestParam String proposalId,
            @RequestParam String boardId,
            @RequestParam String action, // "pass" | "reject"
            @RequestParam(required = false) String content,
            HttpSession session) {

        Map<String, String> result = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                result.put("status", "error");
                result.put("message", "Chưa đăng nhập!");
                return result;
            }

            Board board = boardRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên hội đồng của tài khoản hiện tại!"));

            String resolvedBoardId = board.getId();
            if (boardId != null && !boardId.isBlank() && !board.getId().equals(boardId)) {
                resolvedBoardId = board.getId();
            }

            Proposal p = proposalService.submitBoardVote(proposalId, resolvedBoardId, action, content);

            String voteResult = "pass".equals(action) ? "Thông qua" : "Từ chối";

            notificationController.send(null, p.getMangaka().getUser().getId(),
                    "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                    "/manga/mangaka/my-projects");

            notificationController.send("tantou", null,
                    "Dự án '" + p.getSeriesName() + "' đã nhận phiếu: " + voteResult,
                    "/manga/tantou");

            result.put("status", "success");
            result.put("proposalId", proposalId);
            result.put("newStatus", p.getStatus());
            result.put("message", "Đã ghi nhận phiếu bầu: " + voteResult);

        } catch (RuntimeException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
