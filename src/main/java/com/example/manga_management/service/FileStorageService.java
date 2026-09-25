package com.example.manga_management.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nơi duy nhất đọc/ghi file upload. Mọi file nằm dưới {@code app.upload.root}
 * (mặc định {@code uploads/}), KHÔNG nằm trong {@code src/main/resources/static}
 * nên vẫn hoạt động khi chạy bằng JAR.
 *
 * <p>Các hàm nhận/trả "đường dẫn công khai" dạng {@code /MangaPage/PG00001.png}
 * — chính là giá trị đang lưu trong DB và là URL người dùng truy cập.
 * Dữ liệu seed cũ vẫn nằm trong {@code classpath:/static/} nên các hàm đọc/copy
 * có fallback sang đó.
 */
@Service
public class FileStorageService {

    public static final String DIR_MANGA_PAGE = "MangaPage";
    public static final String DIR_SUBMISSION = "Submission";
    public static final String DIR_AVATARS = "avatars";
    public static final String DIR_PROPOSAL = "proposal";
    public static final String DIR_SERIES_DEFENSE = "series-defense";
    public static final String DIR_TANTOU_PROFILE = "tantou-profile";

    /** Các thư mục con được phép — cũng là danh sách được UploadResourceConfig phục vụ. */
    public static final Set<String> ALLOWED_DIRS = Set.of(
            DIR_MANGA_PAGE, DIR_SUBMISSION, DIR_AVATARS, DIR_PROPOSAL,
            DIR_SERIES_DEFENSE, DIR_TANTOU_PROFILE);

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");
    public static final Set<String> AVATAR_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    public static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path root;

    public FileStorageService(@Value("${app.upload.root:uploads}") String uploadRoot) {
        this.root = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /**
     * Lưu file người dùng upload. Tên file do server sinh ({@code baseName} +
     * đuôi suy ra từ NỘI DUNG file), không dùng {@code getOriginalFilename()}.
     *
     * @return đường dẫn công khai, vd {@code /proposal/PPS001.pdf}
     * @throws IllegalArgumentException nếu file rỗng hoặc nội dung không thuộc whitelist
     */
    public String saveUpload(String dir, String baseName, MultipartFile file, Set<String> allowedExtensions)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn file");
        }
        Path dirPath = resolveDir(dir);
        checkFileName(baseName);
        String extension;
        try (InputStream in = file.getInputStream()) {
            extension = detectExtension(in.readNBytes(16), allowedExtensions);
        }
        String fileName = baseName + extension;
        Files.createDirectories(dirPath);
        Path target = resolveInside(dirPath, fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/" + dir + "/" + fileName;
    }

    /**
     * Lưu mảng byte (vd ảnh canvas giải mã từ base64). Đuôi trong {@code fileName}
     * phải thuộc whitelist và khớp với nội dung thật của byte.
     */
    public String saveBytes(String dir, String fileName, byte[] bytes, Set<String> allowedExtensions)
            throws IOException {
        Path dirPath = resolveDir(dir);
        checkFileName(fileName);
        String requested = extensionOf(fileName);
        if (!allowedExtensions.contains(requested)) {
            throw new IllegalArgumentException("Loại file không được phép");
        }
        String actual = detectExtension(bytes, allowedExtensions);
        if (!sameKind(requested, actual)) {
            throw new IllegalArgumentException("Nội dung file không khớp với định dạng " + requested);
        }
        Files.createDirectories(dirPath);
        Files.write(resolveInside(dirPath, fileName), bytes);
        return "/" + dir + "/" + fileName;
    }

    /**
     * Copy một file đã lưu (hoặc file seed trong classpath) sang thư mục/tên mới.
     *
     * @return đường dẫn công khai của bản copy, hoặc null nếu file nguồn không tồn tại
     */
    public String copy(String sourcePublicPath, String targetDir, String targetFileName) throws IOException {
        Path dirPath = resolveDir(targetDir);
        checkFileName(targetFileName);
        Path target = resolveInside(dirPath, targetFileName);
        String targetPublic = "/" + targetDir + "/" + targetFileName;

        Path source = resolvePublic(sourcePublicPath);
        if (source != null && Files.isRegularFile(source)) {
            Files.createDirectories(dirPath);
            if (!source.equals(target)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetPublic;
        }
        // Fallback: dữ liệu seed cũ còn nằm trong classpath:/static/
        ClassPathResource legacy = legacyResource(sourcePublicPath);
        if (legacy != null && legacy.exists()) {
            Files.createDirectories(dirPath);
            try (InputStream in = legacy.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetPublic;
        }
        return null;
    }

    public boolean exists(String publicPath) {
        Path path = resolvePublic(publicPath);
        if (path != null && Files.isRegularFile(path)) {
            return true;
        }
        ClassPathResource legacy = legacyResource(publicPath);
        return legacy != null && legacy.exists();
    }

    /** Đọc toàn bộ file; ném {@link java.io.FileNotFoundException} nếu không có. */
    public byte[] read(String publicPath) throws IOException {
        Path path = resolvePublic(publicPath);
        if (path != null && Files.isRegularFile(path)) {
            return Files.readAllBytes(path);
        }
        ClassPathResource legacy = legacyResource(publicPath);
        if (legacy != null && legacy.exists()) {
            try (InputStream in = legacy.getInputStream()) {
                return in.readAllBytes();
            }
        }
        throw new java.io.FileNotFoundException(String.valueOf(publicPath));
    }

    /** Xóa file (nếu có). Đường dẫn không hợp lệ bị bỏ qua thay vì ném lỗi. */
    public void delete(String publicPath) throws IOException {
        Path path = resolvePublic(publicPath);
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Đổi đường dẫn công khai ({@code /MangaPage/x.png}) thành Path thật dưới root.
     *
     * @return null nếu đường dẫn không hợp lệ hoặc trỏ ra ngoài root / ngoài thư mục cho phép
     */
    public Path resolvePublic(String publicPath) {
        if (publicPath == null || publicPath.isBlank() || publicPath.indexOf('\0') >= 0
                || publicPath.indexOf('\\') >= 0 || !publicPath.startsWith("/")) {
            return null;
        }
        Path resolved = root.resolve(publicPath.substring(1)).normalize();
        if (!resolved.startsWith(root) || resolved.getNameCount() != root.getNameCount() + 2) {
            return null;
        }
        String dir = resolved.getName(root.getNameCount()).toString();
        return ALLOWED_DIRS.contains(dir) ? resolved : null;
    }

    /** Resource seed cũ trong classpath:/static/ — chỉ với đường dẫn đã qua kiểm tra. */
    private ClassPathResource legacyResource(String publicPath) {
        return resolvePublic(publicPath) == null ? null : new ClassPathResource("static" + publicPath);
    }

    private Path resolveDir(String dir) {
        if (dir == null || !ALLOWED_DIRS.contains(dir)) {
            throw new IllegalArgumentException("Thư mục upload không hợp lệ");
        }
        return root.resolve(dir).normalize();
    }

    private static void checkFileName(String fileName) {
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }
    }

    private static Path resolveInside(Path dir, String fileName) {
        Path resolved = dir.resolve(fileName).normalize();
        if (!dir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Đường dẫn file không hợp lệ");
        }
        return resolved;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean sameKind(String requested, String actual) {
        if (requested.equals(actual)) {
            return true;
        }
        return Set.of(".jpg", ".jpeg").contains(requested) && Set.of(".jpg", ".jpeg").contains(actual);
    }

    /** Nhận dạng định dạng theo magic bytes và bắt buộc nằm trong whitelist. */
    static String detectExtension(byte[] head, Set<String> allowedExtensions) {
        String detected = null;
        if (startsWith(head, 0x89, 'P', 'N', 'G')) {
            detected = ".png";
        } else if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            detected = ".jpg";
        } else if (startsWith(head, 'R', 'I', 'F', 'F') && head.length >= 12
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            detected = ".webp";
        } else if (startsWith(head, 'G', 'I', 'F', '8')) {
            detected = ".gif";
        } else if (startsWith(head, '%', 'P', 'D', 'F')) {
            detected = ".pdf";
        }
        boolean allowed = detected != null && (allowedExtensions.contains(detected)
                || (".jpg".equals(detected) && allowedExtensions.contains(".jpeg")));
        if (!allowed) {
            throw new IllegalArgumentException("Loại file không được hỗ trợ");
        }
        return detected;
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head == null || head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
