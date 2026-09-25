package com.example.manga_management.entity;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID")
    private Long id;

    @Column(name = "Content", nullable = false, length = 255)
    private String content;

    @Column(name = "Role", length = 20)
    private String role;

    @Column(name = "UserID", length = 6)
    private String userId;

    @Column(name = "Link", length = 255)
    private String link;

    @Column(name = "Type", length = 40)
    private String type;

    @Column(name = "ReferenceKey", length = 120)
    private String referenceKey;

    @Column(name = "IsRead", nullable = false)
    private boolean isRead = false;

    // Tự động điền thời gian lúc insert
    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
