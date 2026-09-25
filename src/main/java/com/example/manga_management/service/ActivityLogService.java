package com.example.manga_management.service;

import com.example.manga_management.entity.ActivityLog;
import com.example.manga_management.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

/** Ghi 1 dòng nhật ký hoạt động cho user. Dùng ở màn "Lịch sử hoạt động" của admin. */
@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void log(String userId, String type, String description) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        ActivityLog entry = ActivityLog.builder()
                .userId(userId)
                .type(type)
                .description(description)
                .build();
        activityLogRepository.save(entry);
    }
}
