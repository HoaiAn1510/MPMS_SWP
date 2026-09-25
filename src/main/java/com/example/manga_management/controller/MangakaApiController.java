package com.example.manga_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.MangakaRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mangaka")
@RequiredArgsConstructor
public class MangakaApiController {

    private final AssistantRepository assistantRepository;
    private final MangakaRepository mangakaRepository;

    @GetMapping("/{mangakaId}/assistants")
    public List<Assistant> getAssistants(@PathVariable String mangakaId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Mangaka mangaka = mangakaRepository.findById(mangakaId).orElse(null);
        if (user == null || mangaka == null || mangaka.getUser() == null
                || !mangaka.getUser().getId().equals(user.getId())) {
            return List.of();
        }
        return assistantRepository.findByMangakaId(mangakaId);
    }

}
