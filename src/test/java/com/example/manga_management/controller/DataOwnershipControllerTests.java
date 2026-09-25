package com.example.manga_management.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import com.example.manga_management.entity.Submission;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.ChapterRepository;
import com.example.manga_management.repository.MangaPageRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.VoteSessionRepository;
import com.example.manga_management.service.ActivityLogService;
import com.example.manga_management.service.BookJacketStorageService;
import com.example.manga_management.service.DataAccessService;
import com.example.manga_management.service.FileStorageService;
import com.example.manga_management.service.ProposalService;
import com.example.manga_management.support.OwnershipFixture;

/** IDOR: người dùng đã đăng nhập nhưng KHÔNG liên quan tới dữ liệu phải bị từ chối. */
class DataOwnershipControllerTests {

    private OwnershipFixture f;
    private DataAccessService access;
    private ChapterRepository chapterRepository;
    private MangaPageRepository mangaPageRepository;
    private SeriesRepository seriesRepository;
    private SubmissionRepository submissionRepository;

    @BeforeEach
    void setUp() {
        f = new OwnershipFixture();
        access = new DataAccessService(mock(ProposalRepository.class), mock(VoteSessionRepository.class),
                mock(AssistantRepository.class));
        chapterRepository = mock(ChapterRepository.class);
        mangaPageRepository = mock(MangaPageRepository.class);
        seriesRepository = mock(SeriesRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        when(chapterRepository.findById("CHP001")).thenReturn(Optional.of(f.chapter));
        when(seriesRepository.findById("SER001")).thenReturn(Optional.of(f.series));
        when(mangaPageRepository.findById("PG00001")).thenReturn(Optional.of(f.page));
    }

    private static MockHttpSession sessionOf(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", user);
        return session;
    }

    @Test
    void chapterPagesRejectedForUnrelatedUsers() {
        ChapterController controller = new ChapterController();
        ReflectionTestUtils.setField(controller, "chapterRepository", chapterRepository);
        ReflectionTestUtils.setField(controller, "mangaPageRepository", mangaPageRepository);
        ReflectionTestUtils.setField(controller, "dataAccessService", access);
        when(mangaPageRepository.findByChapterId("CHP001")).thenReturn(List.of(f.page));

        assertEquals("error", controller.getPagesByChapter("CHP001", sessionOf(f.otherMangakaUser)).get("status"));
        assertEquals("error", controller.getPagesByChapter("CHP001", sessionOf(f.otherTantouUser)).get("status"));
        assertEquals("success", controller.getPagesByChapter("CHP001", sessionOf(f.mangakaUser)).get("status"));
        assertEquals("success", controller.getPagesByChapter("CHP001", sessionOf(f.tantouUser)).get("status"));
    }

    @Test
    void seriesInfoRejectedForUnrelatedUsers() {
        SeriesController controller = new SeriesController();
        ReflectionTestUtils.setField(controller, "seriesRepository", seriesRepository);
        ReflectionTestUtils.setField(controller, "dataAccessService", access);

        Map<String, Object> denied = controller.getSeriesInfo("SER001", sessionOf(f.otherMangakaUser));
        assertEquals("error", denied.get("status"));
        assertEquals("error", controller.getSeriesInfo("SER001", sessionOf(f.assistantUser)).get("status"));
    }

    @Test
    void mangakaCannotChangeSubmissionStatusOfAnotherMangaka() {
        MangakaController controller = mangakaController();
        Submission submission = new Submission();
        submission.setId("SUB001");
        submission.setPageId(f.page);
        submission.setStatus("done");
        when(submissionRepository.findById("SUB001")).thenReturn(Optional.of(submission));

        Map<String, Object> result = controller.updateStatusData("SUB001", "pass", "ok",
                sessionOf(f.otherMangakaUser));

        assertEquals("error", result.get("status"));
        assertEquals("done", submission.getStatus());
        verify(submissionRepository, never()).save(any());
        assertEquals("error", controller.getSubmissionData("SUB001", sessionOf(f.otherMangakaUser)).get("status"));
        assertNotEquals("error", controller.getSubmissionData("SUB001", sessionOf(f.mangakaUser)).get("status"));
    }

    @Test
    void mangakaCannotReadOtherMangakaSeriesOrProjects() {
        MangakaController controller = mangakaController();

        assertEquals("error", controller.getSeriesData("SER001", sessionOf(f.otherMangakaUser)).get("status"));
        assertEquals("error", controller.getChapterData("SER001", "CHP001", sessionOf(f.otherMangakaUser))
                .get("status"));
        assertEquals("error", controller.getPageEditData("SER001", "CHP001", "PG00001",
                sessionOf(f.otherMangakaUser)).get("status"));
        assertEquals("error", controller.myProjectsData("MGK001", new ExtendedModelMap(),
                sessionOf(f.otherMangakaUser)).get("status"));
    }

    private MangakaController mangakaController() {
        MangakaRepository mangakaRepository = mock(MangakaRepository.class);
        when(mangakaRepository.findById("MGK001")).thenReturn(Optional.of(f.mangaka));
        return new MangakaController(mock(ProposalRepository.class), mangakaRepository, seriesRepository,
                chapterRepository, mangaPageRepository, submissionRepository, mock(NotificationController.class),
                mock(AssistantRepository.class), mock(ProposalService.class), mock(ActivityLogService.class),
                mock(VoteSessionRepository.class), mock(BookJacketStorageService.class),
                mock(FileStorageService.class), access);
    }
}
