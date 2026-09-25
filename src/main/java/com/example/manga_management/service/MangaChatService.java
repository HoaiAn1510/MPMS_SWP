package com.example.manga_management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MangaChatService {

    private static final String ACTION_CHAT_TEXT = "chat_text";
    private static final String ACTION_GENERATE_IMAGE = "generate_image";
    private static final String ACTION_EDIT_IMAGE = "edit_image";
    private static final String ACTION_ASK_CLARIFICATION = "ask_clarification";
    private static final int MAX_HISTORY_ITEMS = 4;
    private static final int MAX_HISTORY_CONTENT_CHARS = 500;
    private static final int MAX_CONTEXT_VALUE_CHARS = 700;
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.image.model}")
    private String openaiImageModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String chatPrompt = """
            Báº¡n lÃ  trá»£ lÃ½ AI trong pháº§n má»m SANKYUU, há»— trá»£ mangaka vÃ  assistant lÃ m manga.
            Tráº£ lá»i báº±ng tiáº¿ng Viá»‡t, ngáº¯n gá»n, thá»±c táº¿, Æ°u tiÃªn workflow sáº£n xuáº¥t manga.
            Náº¿u ngÆ°á»i dÃ¹ng há»i vá» báº£n váº½, hÃ£y Ä‘Æ°a gá»£i Ã½ cá»¥ thá»ƒ vá» lineart, shading, background,
            lettering, panel clarity, deadline hoáº·c task.
            """;

    private final String directorPrompt = """
            You are SANKYUU Manga AI Prompt Director. Return JSON only.
            Decide how to handle a Vietnamese manga-production chat request.

            Actions: chat_text, generate_image, edit_image, ask_clarification.
            Intents: background, color, shading, clean_lineart, lettering, expression, composition, reference, general_help.
            Apply modes: new_layer, replace_selection, reference_only, no_apply.

            Routing rules:
            - Advice/analysis/questions => chat_text.
            - Create/draw/reference without image => generate_image.
            - Edit/color/shade/clean/background/expression with image => edit_image.
            - Missing required target/context => ask_clarification.

            Use context.task deadline/status to prioritize urgent production advice.
            Use context.frames for panel/frame-specific guidance.
            Use context.tantouFeedback and context.revisionChecklist as concrete fixes to address.
            Use context.styleBible to preserve series tone, genre, character consistency, and recurring design language.
            If context.selectionMask.present is true, set applyMode replace_selection and make imagePrompt edit only the masked area.

            Image prompt rules: English, concise, production-safe. Preserve layout, character identity, pose,
            camera angle, lineart, balloons/text areas, and page ratio unless explicitly changed. Modify only the
            requested aspect/region. If a mask is provided, edit only transparent masked pixels and preserve everything else.
            No text, watermark, logo, signature, captions, or random new characters.
            Prefer manga lineart/screentone/hatching unless color is requested.

            Schema:
            {"action":"chat_text|generate_image|edit_image|ask_clarification","intent":"background|color|shading|clean_lineart|lettering|expression|composition|reference|general_help","imagePrompt":"","textResponse":"short Vietnamese response","needsImage":false,"applyMode":"new_layer|replace_selection|reference_only|no_apply","warnings":[]}
            """;

    public String generateChatResponse(String prompt) {
        return generateChatResponse(prompt, Map.of(), List.of());
    }

    public String generateChatResponse(String prompt, Map<String, Object> context, List<Map<String, Object>> history) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userRequest", prompt);
        payload.put("context", compactContext(context));
        payload.put("recentHistory", trimHistory(history));
        return callGemini(chatPrompt + "\n\nInput JSON:\n" + toJson(payload));
    }

    public Map<String, Object> planChatAction(
            String message,
            Map<String, Object> context,
            List<Map<String, Object>> history,
            boolean hasImage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userRequest", message == null ? "" : message);
        payload.put("context", compactContext(context));
        payload.put("recentHistory", trimHistory(history));
        payload.put("hasUploadedImage", hasImage);

        String raw = callGemini(directorPrompt + "\n\nInput JSON:\n" + toJson(payload));
        Map<String, Object> decision = parseDecision(raw);

        if (hasImage && ACTION_GENERATE_IMAGE.equals(decision.get("action"))) {
            decision.put("action", ACTION_EDIT_IMAGE);
        }
        if (hasImage) {
            decision.put("needsImage", true);
        }
        normalizeDecision(decision, message, hasImage);
        return decision;
    }

    public String generateImage(String prompt) {
        String url = "https://api.openai.com/v1/images/generations";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiImageModel);
        requestBody.put("prompt", prompt);
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    return toDataUrl((String) data.get(0).get("b64_json"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String editImage(MultipartFile image, MultipartFile mask, String prompt) {
        String url = "https://api.openai.com/v1/images/edits";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openaiApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            Resource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.png";
                }
            };
            body.add("image", imageResource);
            if (mask != null && !mask.isEmpty()) {
                Resource maskResource = new ByteArrayResource(mask.getBytes()) {
                    @Override
                    public String getFilename() {
                        return mask.getOriginalFilename() != null ? mask.getOriginalFilename() : "mask.png";
                    }
                };
                body.add("mask", maskResource);
            }
            body.add("prompt", prompt != null && !prompt.trim().isEmpty() ? prompt : "Edit this manga image safely.");
            body.add("n", 1);
            body.add("size", "1024x1024");
            body.add("response_format", "b64_json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    return toDataUrl((String) data.get(0).get("b64_json"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String callGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel
                + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidateContent = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) candidateContent.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Object text = parts.get(0).get("text");
                        if (text != null) {
                            return text.toString();
                        }
                    }
                }
            }
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                throw new GeminiRateLimitException(extractRetryAfterSeconds(e.getResponseBodyAsString()), e);
            }
            e.printStackTrace();
            return "Xin loi, da co loi xay ra khi goi Gemini API: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "Xin loi, da co loi xay ra khi goi Gemini API: " + e.getMessage();
        }
        return "Xin loi, khong the nhan phan hoi tu AI.";
    }

    private Map<String, Object> parseDecision(String raw) {
        try {
            String json = extractJson(raw);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("action", ACTION_CHAT_TEXT);
            fallback.put("intent", "general_help");
            fallback.put("imagePrompt", "");
            fallback.put("textResponse", raw == null || raw.isBlank()
                    ? "MÃ¬nh chÆ°a hiá»ƒu yÃªu cáº§u nÃ y. Báº¡n mÃ´ táº£ thÃªm giÃºp mÃ¬nh nhÃ©."
                    : raw);
            fallback.put("needsImage", false);
            fallback.put("applyMode", "no_apply");
            fallback.put("warnings", List.of("Gemini khÃ´ng tráº£ JSON há»£p lá»‡, Ä‘Ã£ fallback sang chat text."));
            return fallback;
        }
    }

    private void normalizeDecision(Map<String, Object> decision, String message, boolean hasImage) {
        String action = stringValue(decision.get("action"));
        if (!List.of(ACTION_CHAT_TEXT, ACTION_GENERATE_IMAGE, ACTION_EDIT_IMAGE, ACTION_ASK_CLARIFICATION)
                .contains(action)) {
            action = hasImage ? ACTION_EDIT_IMAGE : ACTION_CHAT_TEXT;
            decision.put("action", action);
        }

        if (stringValue(decision.get("intent")).isBlank()) {
            decision.put("intent", "general_help");
        }
        if (stringValue(decision.get("textResponse")).isBlank()) {
            decision.put("textResponse", switch (action) {
                case ACTION_GENERATE_IMAGE -> "MÃ¬nh sáº½ táº¡o áº£nh vÃ  Ä‘áº·t káº¿t quáº£ vÃ o layer AI má»›i.";
                case ACTION_EDIT_IMAGE -> "MÃ¬nh sáº½ chá»‰nh áº£nh theo yÃªu cáº§u vÃ  Ä‘áº·t káº¿t quáº£ vÃ o layer AI má»›i.";
                case ACTION_ASK_CLARIFICATION -> "Báº¡n nÃ³i rÃµ hÆ¡n muá»‘n sá»­a pháº§n nÃ o nhÃ©?";
                default -> "MÃ¬nh Ä‘Ã£ hiá»ƒu yÃªu cáº§u cá»§a báº¡n.";
            });
        }
        if (stringValue(decision.get("imagePrompt")).isBlank()
                && (ACTION_GENERATE_IMAGE.equals(action) || ACTION_EDIT_IMAGE.equals(action))) {
            decision.put("imagePrompt", buildFallbackImagePrompt(message, hasImage));
        }
        if (stringValue(decision.get("applyMode")).isBlank()) {
            decision.put("applyMode", ACTION_CHAT_TEXT.equals(action) ? "no_apply" : "new_layer");
        }
        if (!(decision.get("warnings") instanceof List<?>)) {
            decision.put("warnings", List.of());
        }
        decision.put("needsImage", hasImage || ACTION_EDIT_IMAGE.equals(action));
    }

    private String buildFallbackImagePrompt(String message, boolean edit) {
        String userRequest = message == null || message.isBlank() ? "improve this manga image" : message;
        return (edit ? "Edit the provided manga image. " : "Create a manga production reference image. ")
                + "User request: " + userRequest + ". "
                + "Preserve manga page ratio and production safety. Do not add text, watermark, logo, or signature. "
                + "Use clean manga composition, readable lineart, and controlled details.";
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private Map<String, Object> compactContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> compact = new HashMap<>();
        context.forEach((key, value) -> {
            Object compactValue = compactValue(value, MAX_CONTEXT_VALUE_CHARS);
            if (compactValue != null) {
                compact.put(key, compactValue);
            }
        });
        return compact;
    }

    private Object compactValue(Object value, int maxChars) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> compact = new HashMap<>();
            mapValue.forEach((key, nestedValue) -> {
                Object compactNested = compactValue(nestedValue, maxChars);
                if (compactNested != null) {
                    compact.put(String.valueOf(key), compactNested);
                }
            });
            return compact.isEmpty() ? null : compact;
        }
        if (value instanceof Collection<?> collectionValue) {
            List<Object> compact = new ArrayList<>();
            int count = 0;
            for (Object item : collectionValue) {
                if (count >= 12) {
                    break;
                }
                Object compactItem = compactValue(item, maxChars);
                if (compactItem != null) {
                    compact.add(compactItem);
                    count++;
                }
            }
            return compact.isEmpty() ? null : compact;
        }
        String text = truncate(stringValue(value), maxChars);
        return text.isBlank() ? null : text;
    }

    private List<Map<String, Object>> trimHistory(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_ITEMS);
        List<Map<String, Object>> compact = new ArrayList<>();
        for (Map<String, Object> item : history.subList(from, history.size())) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("role", stringValue(item.get("role")));
            entry.put("content", truncate(stringValue(item.get("content")), MAX_HISTORY_CONTENT_CHARS));
            if (item.get("isImage") != null) {
                entry.put("isImage", item.get("isImage"));
            }
            putCompact(entry, "action", item.get("action"), 80);
            putCompact(entry, "intent", item.get("intent"), 80);
            compact.add(entry);
        }
        return compact;
    }

    private void putCompact(Map<String, Object> target, String key, Object value, int maxChars) {
        String text = truncate(stringValue(value), maxChars);
        if (!text.isBlank()) {
            target.put(key, text);
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private int extractRetryAfterSeconds(String body) {
        if (body == null) {
            return 60;
        }
        java.util.regex.Matcher retryDelay = java.util.regex.Pattern
                .compile("\\\"retryDelay\\\"\\s*:\\s*\\\"(\\d+)s\\\"")
                .matcher(body);
        if (retryDelay.find()) {
            return Integer.parseInt(retryDelay.group(1));
        }
        java.util.regex.Matcher retryIn = java.util.regex.Pattern
                .compile("retry in ([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (retryIn.find()) {
            return Integer.parseInt(retryIn.group(1));
        }
        return 60;
    }

    public static class GeminiRateLimitException extends RuntimeException {
        private final int retryAfterSeconds;

        public GeminiRateLimitException(int retryAfterSeconds, Throwable cause) {
            super("Gemini quota exceeded. Retry after " + retryAfterSeconds + " seconds.", cause);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private String toDataUrl(String b64) {
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        return b64.startsWith("data:image") ? b64 : "data:image/png;base64," + b64;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
