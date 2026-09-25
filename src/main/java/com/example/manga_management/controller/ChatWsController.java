package com.example.manga_management.controller;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.UserRepository;
import com.example.manga_management.service.ChatService;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendRequest request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return;
        }
        User sender = userRepository.findById(principal.getName()).orElse(null);
        if (sender == null) {
            return;
        }
        try {
            chatService.sendMessage(sender, request.receiverId(), request.content());
        } catch (IllegalArgumentException | SecurityException e) {
            e.printStackTrace();
        }
    }

    public record ChatSendRequest(String receiverId, String content) {
    }
}
