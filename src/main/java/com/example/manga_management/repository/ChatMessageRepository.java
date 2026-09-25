package com.example.manga_management.repository;

import com.example.manga_management.entity.ChatMessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    Optional<ChatMessage> findTopByOrderByIdDesc();

    @Query("select m from ChatMessage m "
            + "where (m.sender.id = :a and m.receiver.id = :b) or (m.sender.id = :b and m.receiver.id = :a) "
            + "order by m.createdAt asc")
    List<ChatMessage> findThread(@Param("a") String a, @Param("b") String b);

    long countByReceiver_IdAndIsReadFalse(String receiverId);

    long countBySender_IdAndReceiver_IdAndIsReadFalse(String senderId, String receiverId);

    @Modifying
    @Query("update ChatMessage m set m.isRead = true "
            + "where m.sender.id = :partnerId and m.receiver.id = :me and m.isRead = false")
    int markThreadRead(@Param("partnerId") String partnerId, @Param("me") String me);
}
