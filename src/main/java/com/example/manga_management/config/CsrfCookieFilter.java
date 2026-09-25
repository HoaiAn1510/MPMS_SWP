package com.example.manga_management.config;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security tạo CSRF token lười (chỉ khi có ai đọc). Đọc token ở mỗi
 * request để cookie XSRF-TOKEN luôn được gửi xuống trình duyệt, nhờ đó
 * csrf.js có sẵn token để gắn vào header X-XSRF-TOKEN.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
