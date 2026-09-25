package com.example.manga_management.service;

import com.example.manga_management.entity.BoardProposalComment;
import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.repository.BoardProposalCommentRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class EditorialAiService {

    private static final int MAX_DRAFT_CHARS = 1200;
    private static final int MAX_COMMENT_CHARS = 700;
    private static final String PROMPT = """
            You are SANKYUU Editorial AI for a professional manga production workflow.
            Return valid JSON only. Write all user-facing values in Vietnamese.

            The caller is either a Tantou editor or an Editorial Board member.
            Base every claim on the supplied context. Do not pretend to have read PDF files; file paths only prove that
            a document exists. Separate observed facts from recommendations. Be concise, specific and actionable. Keep the complete response under 900 tokens.
            The Tantou Board submission dossier is prepared only after Tantou approval. When proposal status is new,
            hasBoardSubmissionDossier=false is expected and must never be reported as a risk or missing author task.

            Tantou modes:
            - review_assist: create a review checklist, concrete feedback, risks and a recommendation.
            - feedback_polish: rewrite the supplied draft into respectful, clear production feedback.
            Board modes:
            - board_brief: summarize the proposal, Tantou feedback, prior votes and unresolved questions.
            - decision_summary: compare strengths/risks and suggest pass, reject or revision without making the vote.

            Output schema:
            {
              "content":"short overall summary",
              "sections":{
                "checklist":[], "feedback":"", "strengths":[], "risks":[], "questions":[]
              },
              "recommendation":"approve|minor_revision|major_revision|reject|pass|revision|undetermined",
              "confidence":"low|medium|high"
            }
            """;

    private final ProposalRepository proposalRepository;
    private final BoardProposalCommentRepository boardProposalCommentRepository;
    private final ChapterRepository chapterRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    public EditorialAiService(
            ProposalRepository proposalRepository,
            BoardProposalCommentRepository boardProposalCommentRepository,
            ChapterRepository chapterRepository) {
        this.proposalRepository = proposalRepository;
        this.boardProposalCommentRepository = boardProposalCommentRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> assist(String role, String mode, String proposalId, String draft) {
        Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            return error("Kh\u00f4ng t\u00ecm th\u1ea5y \u0111\u1ec1 xu\u1ea5t: " + proposalId);
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("role", role);
        input.put("mode", mode);
        input.put("draft", truncate(draft, MAX_DRAFT_CHARS));
        input.put("proposal", proposalContext(proposal));
        input.put("chapters", chapterContext(proposal));
        input.put("boardFeedback", boardFeedbackContext(proposalId));

        String raw = callGemini(PROMPT + "\nInput JSON:\n" + toJson(input));
        Map<String, Object> parsed = parseJson(raw);
        return normalize(role, mode, proposalId, parsed);
    }

    private Map<String, Object> proposalContext(Proposal proposal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", proposal.getId());
        context.put("seriesName", proposal.getSeriesName());
        context.put("genre", proposal.getGenre());
        context.put("status", proposal.getStatus());
        context.put("createdAt", dateValue(proposal.getCreatedAt()));
        context.put("reviewedAt", dateValue(proposal.getReviewedAt()));
        context.put("revisionDeadline", dateValue(proposal.getRevisionDeadline()));
        context.put("tantouFeedback", truncate(proposal.getComment(), MAX_COMMENT_CHARS));
        context.put("hasManuscript", hasText(proposal.getFilePath()));
        context.put("hasBoardSubmissionDossier", hasText(proposal.getFileOfTantou()));
        if (proposal.getMangaka() != null && proposal.getMangaka().getUser() != null) {
            context.put("mangaka", proposal.getMangaka().getUser().getFullname());
        }
        if (proposal.getSeries() != null) {
            context.put("seriesStatus", proposal.getSeries().getStatus());
            context.put("seriesDescription", truncate(proposal.getSeries().getDescription(), MAX_COMMENT_CHARS));
        }
        return context;
    }

    private List<Map<String, Object>> chapterContext(Proposal proposal) {
        if (proposal.getSeries() == null) {
            return List.of();
        }
        List<Chapter> chapters = chapterRepository.findBySeriesId(proposal.getSeries().getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Chapter chapter : chapters.stream().limit(12).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", chapter.getId());
            item.put("name", chapter.getChapterName());
            item.put("number", chapter.getChapterNumber());
            item.put("status", chapter.getStatus());
            item.put("deadline", dateValue(chapter.getDeadline()));
            item.put("tantouComment", truncate(chapter.getTantouComment(), MAX_COMMENT_CHARS));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> boardFeedbackContext(String proposalId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BoardProposalComment comment : boardProposalCommentRepository.findByProposal_Id(proposalId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", comment.getAction());
            item.put("content", truncate(comment.getContent(), MAX_COMMENT_CHARS));
            item.put("createdAt", dateValue(comment.getCreatedAt()));
            if (comment.getBoard() != null && comment.getBoard().getUser() != null) {
                item.put("reviewer", comment.getBoard().getUser().getFullname());
            }
            result.add(item);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String callGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel
                + ":generateContent?key=" + geminiApiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.25);
        generationConfig.put("maxOutputTokens", 3000);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", generationConfig);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EditorialAiException("Gemini kh\u00f4ng tr\u1ea3 v\u1ec1 k\u1ebft qu\u1ea3.");
            }
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new EditorialAiException("Gemini kh\u00f4ng tr\u1ea3 v\u1ec1 n\u1ed9i dung.");
            }
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = content == null
                    ? List.of()
                    : (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty() || parts.get(0).get("text") == null) {
                throw new EditorialAiException("Gemini kh\u00f4ng tr\u1ea3 v\u1ec1 n\u1ed9i dung.");
            }
            return parts.get(0).get("text").toString();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                throw new EditorialAiRateLimitException(extractRetrySeconds(e.getResponseBodyAsString()), e);
            }
            throw new EditorialAiException("Kh\u00f4ng th\u1ec3 g\u1ecdi Gemini l\u00fac n\u00e0y.", e);
        } catch (EditorialAiException e) {
            throw e;
        } catch (Exception e) {
            throw new EditorialAiException("Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i t\u1edbi Gemini l\u00fac n\u00e0y.", e);
        }
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            String json = raw == null ? "{}" : raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
            }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("content", raw == null || raw.isBlank()
                    ? "AI ch\u01b0a tr\u1ea3 v\u1ec1 n\u1ed9i dung."
                    : raw.trim());
            fallback.put("sections", Map.of());
            fallback.put("recommendation", "undetermined");
            fallback.put("confidence", "low");
            return fallback;
        }
    }

    private Map<String, Object> normalize(
            String role, String mode, String proposalId, Map<String, Object> parsed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("type", mode.startsWith(role + "_") ? mode : role + "_" + mode);
        result.put("proposalId", proposalId);
        result.put("content", String.valueOf(parsed.getOrDefault("content", "")));
        result.put("sections", parsed.get("sections") instanceof Map<?, ?> ? parsed.get("sections") : Map.of());
        result.put("recommendation", String.valueOf(parsed.getOrDefault("recommendation", "undetermined")));
        result.put("confidence", String.valueOf(parsed.getOrDefault("confidence", "low")));
        result.put("applyTarget", "tantou".equals(role) ? "tantouComment" : "boardComment");
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "error");
        result.put("message", message);
        return result;
    }

    private int extractRetrySeconds(String body) {
        if (body != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\\"retryDelay\\\"\\s*:\\s*\\\"(\\d+)s\\\"")
                    .matcher(body);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 60;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String dateValue(Object value) {
        return value == null ? "" : value.toString();
    }

    public static class EditorialAiRateLimitException extends RuntimeException {
        private final int retryAfterSeconds;

        public EditorialAiRateLimitException(int retryAfterSeconds, Throwable cause) {
            super("Gemini quota exceeded", cause);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class EditorialAiException extends RuntimeException {
        public EditorialAiException(String message) {
            super(message);
        }

        public EditorialAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
