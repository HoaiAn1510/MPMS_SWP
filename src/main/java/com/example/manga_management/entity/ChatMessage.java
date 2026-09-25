package com.example.manga_management.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatmessage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @Column(name = "ChatMessageID", length = 6)
    private String id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "SenderUsername", referencedColumnName = "Username", nullable = false)
    private User sender;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "ReceiverUsername", referencedColumnName = "Username", nullable = false)
    private User receiver;

    @Column(name = "Content", nullable = false, length = 1000)
    private String content;

    @Builder.Default
    @Column(name = "IsRead", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    public String getSenderId() {
        return sender != null ? sender.getId() : null;
    }

    @Transient
    public String getReceiverId() {
        return receiver != null ? receiver.getId() : null;
    }
}
