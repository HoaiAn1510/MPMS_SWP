package com.example.manga_management.repository;

import com.example.manga_management.entity.ActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(String userId);
}
