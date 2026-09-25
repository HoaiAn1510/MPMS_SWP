package com.example.manga_management.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BookJacketStorageService {

    public static final String PUBLIC_PATH = "/bookjackets/";

    private final Path bookJacketDirectory;

    public BookJacketStorageService(@Value("${app.upload.root:uploads}") String uploadRoot) {
        this.bookJacketDirectory = Path.of(uploadRoot)
                .toAbsolutePath()
                .normalize()
                .resolve("bookjackets");
    }

    public String store(MultipartFile file, String seriesId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file ảnh bìa!");
        }

        String extension = resolveExtension(file);
        Files.createDirectories(bookJacketDirectory);

        // Keep the public path within Series.BookJacket's current 30-character limit.
        String version = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String fileName = seriesId + "-" + version + extension;
        Path destination = bookJacketDirectory.resolve(fileName).normalize();
        if (!destination.getParent().equals(bookJacketDirectory)) {
            throw new IOException("Đường dẫn ảnh bìa không hợp lệ");
        }

        Path temporaryFile = Files.createTempFile(bookJacketDirectory, seriesId + "-", ".upload");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }

        return PUBLIC_PATH + fileName;
    }

    public void deleteIfManaged(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PATH)) {
            return;
        }
        String fileName = publicPath.substring(PUBLIC_PATH.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }
        try {
            Files.deleteIfExists(bookJacketDirectory.resolve(fileName).normalize());
        } catch (IOException ignored) {
            // A stale old file must not make an otherwise successful cover update fail.
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.lastIndexOf('.') >= 0) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }

        String contentType = file.getContentType();
        boolean png = ".png".equals(extension) || "image/png".equalsIgnoreCase(contentType);
        boolean jpeg = ".jpg".equals(extension) || ".jpeg".equals(extension)
                || "image/jpeg".equalsIgnoreCase(contentType);
        if (!png && !jpeg) {
            throw new IllegalArgumentException("Ảnh bìa chỉ hỗ trợ file PNG, JPG hoặc JPEG!");
        }
        return png ? ".png" : ".jpg";
    }
}
