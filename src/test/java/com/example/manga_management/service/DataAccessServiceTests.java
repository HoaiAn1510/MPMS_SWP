package com.example.manga_management.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.VoteSession;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.VoteSessionRepository;
import com.example.manga_management.support.OwnershipFixture;

class DataAccessServiceTests {

    private OwnershipFixture f;
    private ProposalRepository proposalRepository;
    private VoteSessionRepository voteSessionRepository;
    private AssistantRepository assistantRepository;
    private DataAccessService service;

    @BeforeEach
    void setUp() {
        f = new OwnershipFixture();
        proposalRepository = mock(ProposalRepository.class);
        voteSessionRepository = mock(VoteSessionRepository.class);
        assistantRepository = mock(AssistantRepository.class);
        service = new DataAccessService(proposalRepository, voteSessionRepository, assistantRepository);
        when(proposalRepository.findById("PPS001")).thenReturn(Optional.of(f.proposal));
    }

    @Test
    void seriesInfoOnlyForOwnerTantouBoardAdmin() {
        assertTrue(service.canViewSeriesInfo(f.series, f.mangakaUser));
        assertTrue(service.canViewSeriesInfo(f.series, f.tantouUser));
        assertTrue(service.canViewSeriesInfo(f.series, f.boardUser));
        assertTrue(service.canViewSeriesInfo(f.series, f.adminUser));
        assertFalse(service.canViewSeriesInfo(f.series, f.otherMangakaUser));
        assertFalse(service.canViewSeriesInfo(f.series, f.otherTantouUser));
        assertFalse(service.canViewSeriesInfo(f.series, f.assistantUser));
        assertFalse(service.canViewSeriesInfo(f.series, null));
    }

    @Test
    void seriesContentIncludesOnlyAssistantsOfTheOwnerMangaka() {
        Assistant mine = new Assistant();
        mine.setId("AST001");
        mine.setMangaka(f.mangaka);
        when(assistantRepository.findByUserId("USR_AST")).thenReturn(Optional.of(mine));

        assertTrue(service.canViewSeriesContent(f.series, f.assistantUser));
        assertFalse(service.canViewSeriesContent(f.series, f.boardUser));
        assertFalse(service.canViewSeriesContent(f.series, f.otherMangakaUser));

        Assistant foreign = new Assistant();
        Mangaka otherMangaka = new Mangaka();
        otherMangaka.setId("MGK999");
        foreign.setMangaka(otherMangaka);
        when(assistantRepository.findByUserId("USR_AST")).thenReturn(Optional.of(foreign));
        assertFalse(service.canViewSeriesContent(f.series, f.assistantUser));
    }

    @Test
    void proposalFileOnlyForOwnerTantouAndBoardWhenOnBoardTable() {
        assertTrue(service.canAccessSensitiveFile(f.mangakaUser, "/proposal/PPS001.pdf"));
        assertTrue(service.canAccessSensitiveFile(f.tantouUser, "/proposal/PPS001.pdf"));
        assertTrue(service.canAccessSensitiveFile(f.adminUser, "/proposal/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.otherMangakaUser, "/proposal/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.otherTantouUser, "/proposal/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.assistantUser, "/proposal/PPS001.pdf"));
        // Hội đồng chỉ thấy khi đề xuất đã lên bàn hội đồng
        assertFalse(service.canAccessSensitiveFile(f.boardUser, "/proposal/PPS001.pdf"));
        f.proposal.setStatus("board_check");
        assertTrue(service.canAccessSensitiveFile(f.boardUser, "/proposal/PPS001.pdf"));
        f.proposal.setStatus("started");
        f.proposal.setBoardReviewedAt(LocalDateTime.now());
        assertTrue(service.canAccessSensitiveFile(f.boardUser, "/proposal/PPS001.pdf"));
    }

    @Test
    void tantouProfileExcludesMangaka() {
        f.proposal.setStatus("board_check");
        assertTrue(service.canAccessSensitiveFile(f.tantouUser, "/tantou-profile/PPS001.pdf"));
        assertTrue(service.canAccessSensitiveFile(f.boardUser, "/tantou-profile/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.mangakaUser, "/tantou-profile/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.otherTantouUser, "/tantou-profile/PPS001.pdf"));
    }

    @Test
    void defenseFileForBoardTantouOwnerOnly() {
        VoteSession vs = new VoteSession();
        vs.setId("VS1P8X");
        vs.setSeries(f.series);
        when(voteSessionRepository.findById("VS1P8X")).thenReturn(Optional.of(vs));

        assertTrue(service.canAccessSensitiveFile(f.boardUser, "/series-defense/VS1P8X.pdf"));
        assertTrue(service.canAccessSensitiveFile(f.tantouUser, "/series-defense/VS1P8X.pdf"));
        assertTrue(service.canAccessSensitiveFile(f.mangakaUser, "/series-defense/VS1P8X.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.otherTantouUser, "/series-defense/VS1P8X.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.otherMangakaUser, "/series-defense/VS1P8X.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.assistantUser, "/series-defense/VS1P8X.pdf"));
    }

    @Test
    void sensitiveFileRejectsUnknownOrMalformedPaths() {
        assertFalse(service.canAccessSensitiveFile(f.mangakaUser, "/proposal/PPS999.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.adminUser, "/proposal/../application.properties"));
        assertFalse(service.canAccessSensitiveFile(f.adminUser, "/proposal/sub/PPS001.pdf"));
        assertFalse(service.canAccessSensitiveFile(f.adminUser, "/MangaPage/PG1.png"));
        assertFalse(service.canAccessSensitiveFile(null, "/proposal/PPS001.pdf"));
    }
}
