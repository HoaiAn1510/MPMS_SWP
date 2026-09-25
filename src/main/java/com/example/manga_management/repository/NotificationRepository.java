package com.example.manga_management.repository;

import com.example.manga_management.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where n.userId = :userId or n.role = :role
            order by n.createdAt desc
            """)
    List<Notification> findInbox(@Param("userId") String userId, @Param("role") String role);

    @Query("""
            select count(n) from Notification n
            where (n.userId = :userId or n.role = :role)
              and n.isRead = false
            """)
    long countUnread(@Param("userId") String userId, @Param("role") String role);

    @Query("""
            select count(n) from Notification n
            where n.userId = :userId
              and n.type = :type
              and n.referenceKey = :referenceKey
            """)
    long countByUserIdAndTypeAndReferenceKey(
            @Param("userId") String userId,
            @Param("type") String type,
            @Param("referenceKey") String referenceKey);

    @Query("""
            select count(n) from Notification n
            where n.role = :role
              and n.type = :type
              and n.referenceKey = :referenceKey
            """)
    long countByRoleAndTypeAndReferenceKey(
            @Param("role") String role,
            @Param("type") String type,
            @Param("referenceKey") String referenceKey);

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdOrRoleOrderByCreatedAtDesc(String userId, String role);
}
