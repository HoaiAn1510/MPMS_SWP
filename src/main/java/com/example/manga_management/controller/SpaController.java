package com.example.manga_management.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.ProposalRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class SpaController {

    private final MangakaRepository mangakaRepository;
    private final ProposalRepository proposalRepository;

    public SpaController(MangakaRepository mangakaRepository, ProposalRepository proposalRepository) {
        this.mangakaRepository = mangakaRepository;
        this.proposalRepository = proposalRepository;
    }

    @GetMapping({
            "/manga/mangaka/myseries/{seriesId}",
            "/manga/mangaka/myseries/{sid}/{cid}",
            "/manga/mangaka/myseries/{sid}/{cid}/{pid}/edit",
            "/manga/mangaka/submission/{id}/edit"
    })
    public String mangakaApp(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentUserId", user.getId());
        Mangaka mangaka = mangakaRepository.findByUser(user).orElse(null);
        model.addAttribute("mangaka", mangaka);
        if (mangaka != null) {
            model.addAttribute("allProposals", proposalRepository.findByMangaka_Id(mangaka.getId()));
            model.addAttribute("approvedList",
                    proposalRepository.findByStatusInAndMangaka_Id(List.of("approved", "board_check", "passed"),
                            mangaka.getId()));
            model.addAttribute("revisionList",
                    proposalRepository.findByStatusAndMangaka_Id("revision", mangaka.getId()));
            model.addAttribute("lockedList",
                    proposalRepository.findByStatusAndMangaka_Id("locked", mangaka.getId()));
        }
        return "mangaka";
    }
}
