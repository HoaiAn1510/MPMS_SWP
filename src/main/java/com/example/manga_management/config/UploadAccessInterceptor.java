package com.example.manga_management.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.example.manga_management.entity.User;
import com.example.manga_management.service.DataAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Chặn tải file nhạy cảm (/proposal, /series-defense, /tantou-profile) nếu
 * người dùng không phải người liên quan tới dữ liệu đó. Đăng nhập vẫn do
 * SecurityConfig đảm nhiệm; lớp này bổ sung phân quyền theo dữ liệu.
 */
public class UploadAccessInterceptor implements HandlerInterceptor {

    private final DataAccessService dataAccessService;

    public UploadAccessInterceptor(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Object userObj = request.getSession(false) != null ? request.getSession(false).getAttribute("user") : null;
        User user = userObj instanceof User u ? u : null;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (dataAccessService.canAccessSensitiveFile(user, path)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
