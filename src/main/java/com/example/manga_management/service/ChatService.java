package com.example.manga_management.service;

import com.example.manga_management.entity.Assistant;
import com.example.manga_management.entity.ChatMessage;
import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.TantoEditor;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.ChatMessageRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public boolean canMessage(User a, User b) {
        if (a == null || b == null || a.getId().equals(b.getId())) {
            return false;
        }
        String ra = a.getRole().toLowerCase();
        String rb = b.getRole().toLowerCase();

        if (ra.equals("admin") || rb.equals("admin")) {
            return true;
        }
        if (ra.equals("board") || rb.equals("board")) {
            // Board chỉ chat được với Board khác, Tantou và Admin (admin đã xử lý ở trên).
            String other = ra.equals("board") ? rb : ra;
            return other.equals("board") || other.equals("tantou");
        }
        // Tantou không chat ngang hàng với tantou khác.
        if (isPair(ra, rb, "mangaka", "assistant")) {
            User mangakaUser = ra.equals("mangaka") ? a : b;
            User assistantUser = ra.equals("mangaka") ? b : a;
            Mangaka mangaka = mangakaRepository.findByUserId(mangakaUser.getId()).orElse(null);
            Assistant assistant = assistantRepository.findByUserId(assistantUser.getId()).orElse(null);
            return mangaka != null && assistant != null
                    && assistant.getMangaka() != null
                    && assistant.getMangaka().getId().equals(mangaka.getId());
        }
        if (isPair(ra, rb, "mangaka", "tantou")) {
            User mangakaUser = ra.equals("mangaka") ? a : b;
            User tantouUser = ra.equals("mangaka") ? b : a;
            Mangaka mangaka = mangakaRepository.findByUserId(mangakaUser.getId()).orElse(null);
            TantoEditor editor = tantoEditorRepository.findByUserId(tantouUser.getId()).orElse(null);
            return mangaka != null && editor != null
                    && mangaka.getEditor() != null
                    && mangaka.getEditor().getId().equals(editor.getId());
        }
        return false;
    }

    private boolean isPair(String ra, String rb, String role1, String role2) {
        return (ra.equals(role1) && rb.equals(role2)) || (ra.equals(role2) && rb.equals(role1));
    }

    public List<User> getContacts(User me) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()) && canMessage(me, u))
                .toList();
    }

    @Transactional
    public List<ChatMessage> getThread(User me, String partnerId) {
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        if (!canMessage(me, partner)) {
            throw new SecurityException("Bạn không có quyền nhắn tin với người này");
        }
        chatMessageRepository.markThreadRead(partnerId, me.getId());
        return chatMessageRepository.findThread(me.getId(), partnerId);
    }

    @Transactional
    public ChatMessage sendMessage(User sender, String receiverId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Người nhận không tồn tại"));
        if (!canMessage(sender, receiver)) {
            throw new SecurityException("Bạn không có quyền nhắn tin với người này");
        }

        String lastId = chatMessageRepository.findTopByOrderByIdDesc()
                .map(ChatMessage::getId).orElse("M0000");
        int nextNum = Integer.parseInt(lastId.replaceAll("[^0-9]", "")) + 1;
        String newId = "M" + String.format("%04d", nextNum);

        // saveAndFlush: ID được gán thủ công (không còn IDENTITY tự sinh) nên Hibernate
        // sẽ hoãn câu lệnh INSERT tới lúc flush - phải flush ngay để @CreationTimestamp
        // được điền trước khi broadcast qua WebSocket.
        ChatMessage saved = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .id(newId)
                .sender(sender)
                .receiver(receiver)
                .content(content.trim())
                .isRead(false)
                .build());

        messagingTemplate.convertAndSendToUser(receiverId, "/queue/messages", saved);
        messagingTemplate.convertAndSendToUser(sender.getId(), "/queue/messages", saved);
        return saved;
    }

    public long getUnreadCount(User me) {
        return chatMessageRepository.countByReceiver_IdAndIsReadFalse(me.getId());
    }

    public long getUnreadCountFrom(User me, String partnerId) {
        return chatMessageRepository.countBySender_IdAndReceiver_IdAndIsReadFalse(partnerId, me.getId());
    }

    public ChatMessage getLastMessage(User me, String partnerId) {
        List<ChatMessage> thread = chatMessageRepository.findThread(me.getId(), partnerId);
        return thread.isEmpty() ? null : thread.get(thread.size() - 1);
    }
}
