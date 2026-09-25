package com.example.manga_management.support;

import com.example.manga_management.entity.Chapter;
import com.example.manga_management.entity.MangaPage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.Proposal;
import com.example.manga_management.entity.Series;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;

/**
 * Dựng chuỗi dữ liệu mangaka(USR_MGK) → tantou(USR_TAN) → proposal → series → chapter → page
 * để test phân quyền theo dữ liệu.
 */
public class OwnershipFixture {

    public final User mangakaUser = user("USR_MGK", "MANGAKA");
    public final User tantouUser = user("USR_TAN", "TANTOU");
    public final User otherMangakaUser = user("USR_MGK2", "MANGAKA");
    public final User otherTantouUser = user("USR_TAN2", "TANTOU");
    public final User assistantUser = user("USR_AST", "ASSISTANT");
    public final User boardUser = user("USR_BRD", "BOARD");
    public final User adminUser = user("USR_ADM", "ADMIN");

    public final TantoEditor editor = new TantoEditor();
    public final Mangaka mangaka = new Mangaka();
    public final Proposal proposal = new Proposal();
    public final Series series = new Series();
    public final Chapter chapter = new Chapter();
    public final MangaPage page = new MangaPage();

    public OwnershipFixture() {
        editor.setId("TAN001");
        editor.setUser(tantouUser);
        mangaka.setId("MGK001");
        mangaka.setUser(mangakaUser);
        mangaka.setEditor(editor);
        proposal.setId("PPS001");
        proposal.setMangaka(mangaka);
        proposal.setStatus("started");
        series.setId("SER001");
        series.setProposal(proposal);
        series.setStatus("unfinish");
        chapter.setId("CHP001");
        chapter.setSeries(series);
        chapter.setStatus("unfinish");
        page.setId("PG00001");
        page.setChapter(chapter);
    }

    public static User user(String id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }
}
