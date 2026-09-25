package com.example.manga_management.controller;

import com.example.manga_management.entity.ChatMessage;
import com.example.manga_management.entity.User;
import com.example.manga_management.service.ChatService;

import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/user-chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Nhắn tin nội bộ giữa các tài khoản (danh bạ, lịch sử hội thoại, số tin chưa đọc)")
public class ChatRestController {

    private final ChatService chatService;

    @Operation(summary = "Danh sách liên hệ đã từng nhắn tin + tin nhắn gần nhất")
    @GetMapping("/contacts")
    public List<Map<String, Object>> getContacts(HttpSession session) {
        User me = currentUser(session);
        return chatService.getContacts(me).stream()
                .map(contact -> {
                    ChatMessage last = chatService.getLastMessage(me, contact.getId());
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", contact.getId());
                    dto.put("fullname", contact.getFullname());
                    dto.put("role", contact.getRole());
                    dto.put("avatar", contact.getAvatar());
                    dto.put("unreadCount", chatService.getUnreadCountFrom(me, contact.getId()));
                    dto.put("lastMessage", last != null ? last.getContent() : null);
                    dto.put("lastTime", last != null ? last.getCreatedAt() : null);
                    return dto;
                })
                .toList();
    }

    @Operation(summary = "Toàn bộ lịch sử hội thoại giữa tôi và 1 người liên hệ")
    @GetMapping("/thread/{partnerId}")
    public List<ChatMessage> getThread(@PathVariable String partnerId, HttpSession session) {
        User me = currentUser(session);
        try {
            return chatService.getThread(me, partnerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @Operation(summary = "Tổng số tin nhắn chưa đọc của tôi (hiện badge trên chuông chat)")
    @GetMapping("/unread-count")
    public Map<String, Object> getUnreadCount(HttpSession session) {
        User me = currentUser(session);
        return Map.of("unreadCount", chatService.getUnreadCount(me));
    }

    private User currentUser(HttpSession session) {
        Object userObj = session.getAttribute("user");
        if (!(userObj instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return user;
    }
}
