package com.example.manga_management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Nhật ký hoạt động — ghi lại các hành động do user thực hiện mà không thể suy
 * ra từ dữ liệu sau khi xảy ra (vd xóa trang, sửa kịch bản, giao lại việc).
 * Dùng cho màn "Lịch sử hoạt động" của admin.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User thực hiện hành động. */
    @Column(name = "UserID", length = 6, nullable = false)
    private String userId;

    /** Loại hành động (dùng để chọn icon ở FE), vd: create-chapter, delete-page. */
    @Column(name = "Type", length = 40, nullable = false)
    private String type;

    /** Mô tả hiển thị cho admin đọc. */
    @Column(name = "Description", length = 500, nullable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
