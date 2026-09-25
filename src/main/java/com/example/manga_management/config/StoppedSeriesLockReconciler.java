package com.example.manga_management.config;

import com.example.manga_management.entity.Series;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Vá dữ liệu cũ: series đã "stopped" từ trước khi cascade khoá
 * chapter/huỷ task (NotificationService.cancelUnapprovedTasksForStoppedSeries)
 * tồn tại vẫn còn sót chapter ở status cũ (unfinish/finish/pass) và task chưa
 * huỷ, vì cascade đó chỉ chạy đúng lúc series CHUYỂN sang stopped, không phải
 * rule kiểm tra liên tục. Gọi lại cascade (idempotent) cho mọi series đang
 * stopped mỗi lần app khởi động để đồng bộ dữ liệu cũ.
 */
@Component
@RequiredArgsConstructor
public class StoppedSeriesLockReconciler implements ApplicationRunner {

    private final SeriesRepository seriesRepository;
    private final NotificationService notificationService;

    @Override
    public void run(ApplicationArguments args) {
        for (Series series : seriesRepository.findByStatus("stopped")) {
            notificationService.cancelUnapprovedTasksForStoppedSeries(series);
        }
    }
}
