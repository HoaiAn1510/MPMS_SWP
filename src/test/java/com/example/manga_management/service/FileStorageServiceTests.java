package com.example.manga_management.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes();
    private static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes();

    @TempDir
    Path tempDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(tempDir.toString());
    }

    @Test
    void savesBytesUnderRootAndReturnsPublicPath() throws IOException {
        String path = storage.saveBytes("MangaPage", "PG00001.png", PNG, FileStorageService.IMAGE_EXTENSIONS);

        assertEquals("/MangaPage/PG00001.png", path);
        assertArrayEquals(PNG, Files.readAllBytes(tempDir.resolve("MangaPage").resolve("PG00001.png")));
        assertTrue(storage.exists(path));
        assertArrayEquals(PNG, storage.read(path));
    }

    @Test
    void rejectsBytesWhoseContentDoesNotMatchImageWhitelist() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "PG1.png", HTML, FileStorageService.IMAGE_EXTENSIONS));
        // PDF không nằm trong whitelist ảnh
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "PG1.png", PDF, FileStorageService.IMAGE_EXTENSIONS));
        // Đuôi ngoài whitelist
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "PG1.svg", PNG, FileStorageService.IMAGE_EXTENSIONS));
        // Nội dung là JPG nhưng đuôi là .png
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "PG1.png", JPG, FileStorageService.IMAGE_EXTENSIONS));
    }

    @Test
    void rejectsPathTraversalInDirAndFileName() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("../etc", "a.png", PNG, FileStorageService.IMAGE_EXTENSIONS));
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "../evil.png", PNG, FileStorageService.IMAGE_EXTENSIONS));
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "..\\evil.png", PNG, FileStorageService.IMAGE_EXTENSIONS));
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("MangaPage", "a/b.png", PNG, FileStorageService.IMAGE_EXTENSIONS));
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveBytes("unknown-dir", "a.png", PNG, FileStorageService.IMAGE_EXTENSIONS));
    }

    @Test
    void resolvePublicOnlyAcceptsWhitelistedDirsInsideRoot() {
        assertNull(storage.resolvePublic(null));
        assertNull(storage.resolvePublic("MangaPage/a.png"));
        assertNull(storage.resolvePublic("/../secret.txt"));
        assertNull(storage.resolvePublic("/MangaPage/../../secret.txt"));
        assertNull(storage.resolvePublic("/MangaPage/..\\..\\secret.txt"));
        assertNull(storage.resolvePublic("/MangaPage/sub/a.png"));
        assertNull(storage.resolvePublic("/other/a.png"));
        assertEquals(tempDir.toAbsolutePath().normalize().resolve("MangaPage").resolve("a.png"),
                storage.resolvePublic("/MangaPage/a.png"));
    }

    @Test
    void readAndDeleteIgnoreTraversalPaths() throws IOException {
        Path outside = tempDir.getParent().resolve("outside-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "secret");
        try {
            String traversal = "/MangaPage/../../" + outside.getFileName();
            assertFalse(storage.exists(traversal));
            assertThrows(java.io.FileNotFoundException.class, () -> storage.read(traversal));
            storage.delete(traversal);
            assertTrue(Files.exists(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void uploadIgnoresClientFilenameAndUsesDetectedExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.jsp", "image/jpeg", JPG);

        String path = storage.saveUpload("avatars", "avatar_USR001", file, FileStorageService.AVATAR_EXTENSIONS);

        assertEquals("/avatars/avatar_USR001.jpg", path);
        assertTrue(Files.exists(tempDir.resolve("avatars").resolve("avatar_USR001.jpg")));
    }

    @Test
    void uploadRejectsDisguisedFilesAndEmptyFiles() {
        // Đuôi .pdf nhưng nội dung là HTML
        MockMultipartFile fake = new MockMultipartFile("file", "proposal.pdf", "application/pdf", HTML);
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveUpload("proposal", "PPS001", fake, FileStorageService.PDF_EXTENSIONS));
        // PDF thật nhưng chỗ chỉ nhận ảnh
        MockMultipartFile pdf = new MockMultipartFile("file", "a.png", "image/png", PDF);
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveUpload("avatars", "avatar_1", pdf, FileStorageService.AVATAR_EXTENSIONS));
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveUpload("proposal", "PPS001", empty, FileStorageService.PDF_EXTENSIONS));
        // baseName chứa ký tự nguy hiểm
        MockMultipartFile ok = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF);
        assertThrows(IllegalArgumentException.class,
                () -> storage.saveUpload("proposal", "../PPS001", ok, FileStorageService.PDF_EXTENSIONS));
    }

    @Test
    void copyDuplicatesFileAndReturnsNullWhenSourceMissing() throws IOException {
        storage.saveBytes("MangaPage", "PG1.png", PNG, FileStorageService.IMAGE_EXTENSIONS);

        String copied = storage.copy("/MangaPage/PG1.png", "Submission", "SUB1_assigned.png");

        assertEquals("/Submission/SUB1_assigned.png", copied);
        assertArrayEquals(PNG, storage.read(copied));
        assertNull(storage.copy("/MangaPage/none.png", "Submission", "x.png"));
        // Copy lên chính nó không được làm hỏng file
        assertEquals("/MangaPage/PG1.png", storage.copy("/MangaPage/PG1.png", "MangaPage", "PG1.png"));
        assertArrayEquals(PNG, storage.read("/MangaPage/PG1.png"));
    }

    @Test
    void deleteRemovesStoredFile() throws IOException {
        String path = storage.saveBytes("Submission", "S1.png", PNG, FileStorageService.IMAGE_EXTENSIONS);

        storage.delete(path);

        assertFalse(Files.exists(tempDir.resolve("Submission").resolve("S1.png")));
    }
}
