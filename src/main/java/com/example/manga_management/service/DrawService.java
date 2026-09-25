package com.example.manga_management.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Toàn bộ logic xử lý ảnh vẽ và project nhiều layer. Controller chỉ gọi vào
 * đây, không tự xử lý file/JSON/base64.
 */
@Service
public class DrawService {

    private static final String IMAGE_DIR = "uploads/drawings/";
    private static final String PROJECT_DIR = "uploads/draw-projects/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Lưu ảnh đã gộp (flatten) ─────────────────────────────────────────
    public Map<String, Object> saveFlattenedImage(String imageBase64) {
        try {
            Path dir = Paths.get(IMAGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            byte[] imageBytes = decodeBase64(imageBase64);
            String fileName = "drawing_" + timestamp() + ".png";
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, imageBytes, StandardOpenOption.CREATE);

            return Map.of(
                    "status", "success",
                    "fileName", fileName,
                    "imageBase64", stripDataPrefix(imageBase64)
            );

        } catch (IOException | IllegalArgumentException ex) {
            return errorResult("Lưu ảnh thất bại: " + ex.getMessage());
        }
    }

    // ── Lưu project nhiều layer ──────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveProject(Map<String, Object> requestBody) {
        try {
            String projectName = sanitizeName((String) requestBody.getOrDefault("projectName", "untitled"));
            int canvasWidth = toInt(requestBody.getOrDefault("canvasWidth", 800));
            int canvasHeight = toInt(requestBody.getOrDefault("canvasHeight", 600));

            List<Map<String, Object>> layersInput = (List<Map<String, Object>>) requestBody.get("layers");
            if (layersInput == null || layersInput.isEmpty()) {
                return errorResult("Project không có layer nào để lưu");
            }

            String projectId = projectName + "_" + timestamp();
            Path projectPath = Paths.get(PROJECT_DIR, projectId);
            Files.createDirectories(projectPath);
            ArrayNode layersArray = buildAndPersistLayers(projectPath, layersInput);

            ObjectNode projectMeta = objectMapper.createObjectNode();
            projectMeta.put("projectId", projectId);
            projectMeta.put("projectName", projectName);
            projectMeta.put("canvasWidth", canvasWidth);
            projectMeta.put("canvasHeight", canvasHeight);
            projectMeta.put("savedAt", timestamp());
            projectMeta.set("layers", layersArray);

            Files.writeString(
                    projectPath.resolve("project.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(projectMeta)
            );

            return Map.of(
                    "status", "success",
                    "projectId", projectId,
                    "message", "Đã lưu project với " + layersInput.size() + " layer"
            );

        } catch (IOException | IllegalArgumentException ex) {
            return errorResult("Lưu project thất bại: " + ex.getMessage());
        }
    }

    /**
     * Ghi từng layer thành 1 file PNG riêng, trả về metadata JSON tương ứng.
     */
    private ArrayNode buildAndPersistLayers(Path projectPath, List<Map<String, Object>> layersInput) throws IOException {
        ArrayNode layersArray = objectMapper.createArrayNode();

        int index = 0;
        for (Map<String, Object> layerInput : layersInput) {
            String layerName = (String) layerInput.getOrDefault("name", "Layer " + index);
            boolean visible = Boolean.TRUE.equals(layerInput.get("visible"));
            int opacity = layerInput.get("opacity") != null ? toInt(layerInput.get("opacity")) : 100;
            String layerImageBase64 = (String) layerInput.get("imageBase64");

            String layerFileName = "layer_" + index + ".png";
            if (layerImageBase64 != null && !layerImageBase64.isBlank()) {
                byte[] layerBytes = decodeBase64(layerImageBase64);
                Files.write(projectPath.resolve(layerFileName), layerBytes, StandardOpenOption.CREATE);
            }

            ObjectNode layerMeta = objectMapper.createObjectNode();
            layerMeta.put("order", index);
            layerMeta.put("name", layerName);
            layerMeta.put("visible", visible);
            layerMeta.put("opacity", opacity);
            layerMeta.put("file", layerFileName);
            layersArray.add(layerMeta);

            index++;
        }
        return layersArray;
    }

    // ── Load lại project ─────────────────────────────────────────────────
    public Map<String, Object> loadProject(String projectId) {
        try {
            Path projectPath = Paths.get(PROJECT_DIR, sanitizeName(projectId));
            Path metaPath = projectPath.resolve("project.json");

            if (!Files.exists(metaPath)) {
                return errorResult("Không tìm thấy project: " + projectId);
            }

            JsonNode projectMeta = objectMapper.readTree(metaPath.toFile());
            List<Map<String, Object>> layersOut = new ArrayList<>();

            for (JsonNode layerMeta : projectMeta.get("layers")) {
                String fileName = layerMeta.get("file").asText();
                Path layerFilePath = projectPath.resolve(fileName);

                String layerBase64 = "";
                if (Files.exists(layerFilePath)) {
                    byte[] bytes = Files.readAllBytes(layerFilePath);
                    layerBase64 = Base64.getEncoder().encodeToString(bytes);
                }

                layersOut.add(Map.of(
                        "name", layerMeta.get("name").asText(),
                        "visible", layerMeta.get("visible").asBoolean(),
                        "opacity", layerMeta.get("opacity").asInt(),
                        "imageBase64", layerBase64
                ));
            }

            return Map.of(
                    "status", "success",
                    "projectName", projectMeta.get("projectName").asText(),
                    "canvasWidth", projectMeta.get("canvasWidth").asInt(),
                    "canvasHeight", projectMeta.get("canvasHeight").asInt(),
                    "layers", layersOut
            );

        } catch (IOException ex) {
            return errorResult("Load project thất bại: " + ex.getMessage());
        }
    }

    // ── Danh sách project đã lưu ─────────────────────────────────────────
    public Map<String, Object> listProjects() {
        try {
            Path baseDir = Paths.get(PROJECT_DIR);
            if (!Files.exists(baseDir)) {
                return Map.of("status", "success", "projects", new ArrayList<>());
            }

            List<Map<String, Object>> projects = new ArrayList<>();
            try (var stream = Files.list(baseDir)) {
                for (Path dir : stream.filter(Files::isDirectory).toList()) {
                    Path metaPath = dir.resolve("project.json");
                    if (!Files.exists(metaPath)) {
                        continue;
                    }

                    JsonNode meta = objectMapper.readTree(metaPath.toFile());
                    projects.add(Map.of(
                            "projectId", meta.get("projectId").asText(),
                            "projectName", meta.get("projectName").asText(),
                            "savedAt", meta.get("savedAt").asText(),
                            "layerCount", meta.get("layers").size()
                    ));
                }
            }

            return Map.of("status", "success", "projects", projects);

        } catch (IOException ex) {
            return errorResult("Không thể đọc danh sách project: " + ex.getMessage());
        }
    }
// ── helpers chung ───────────────────────────────────────────────────

    private byte[] decodeBase64(String base64Str) {
        return Base64.getDecoder().decode(stripDataPrefix(base64Str));
    }

    private String stripDataPrefix(String base64Str) {
        return base64Str.contains(",")
                ? base64Str.substring(base64Str.indexOf(",") + 1)
                : base64Str;
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private int toInt(Object value) {
        return Integer.parseInt(value.toString());
    }

    private Map<String, Object> errorResult(String message) {
        return Map.of("status", "error", "message", message);
    }

    /**
     * Loại bỏ ký tự nguy hiểm trong tên file/folder để tránh path traversal (ví
     * dụ project name chứa "../" hoặc ký tự đặc biệt).
     */
    private String sanitizeName(String input) {
        if (input == null || input.isBlank()) {
            return "untitled";
        }
        return input.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
