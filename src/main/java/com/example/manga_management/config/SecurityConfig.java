package com.example.manga_management.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SessionUserAuthenticationFilter sessionUserAuthenticationFilter;

    public SecurityConfig(SessionUserAuthenticationFilter sessionUserAuthenticationFilter) {
        this.sessionUserAuthenticationFilter = sessionUserAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF dạng cookie: JS đọc cookie XSRF-TOKEN rồi gửi lại ở header X-XSRF-TOKEN (xem csrf.js).
                // Dùng handler không mã hoá (plain) vì token do JS đọc thẳng từ cookie.
                // /ws-chat/** (SockJS/STOMP) được bảo vệ bằng kiểm tra Origin của WebSocket (xem WebSocketConfig).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/ws-chat/**"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/login/forgot-password/**",
                                "/logout",
                                "/error")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**", "/swagger-ui.html").hasRole("ADMIN")
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/bookjackets/**", "/webjars/**").permitAll()
                        .requestMatchers("/manga/system-admin/**").hasRole("ADMIN")
                        .requestMatchers("/manga/mangaka/myseries/*/data", "/manga/mangaka/myseries/*/*/data").hasAnyRole("MANGAKA", "TANTOU")
                        .requestMatchers("/manga/mangaka/**").hasRole("MANGAKA")
                        .requestMatchers("/manga/assistant/**").hasRole("ASSISTANT")
                        .requestMatchers("/manga/tantou/**").hasRole("TANTOU")
                        .requestMatchers("/manga/editor/**").hasRole("BOARD")
                        // Các API series dạng "liệt kê/xem thô/tạo" chỉ dùng qua Swagger — giao diện chỉ gọi /api/series/{id}/info.
                        .requestMatchers(HttpMethod.GET, "/api/series", "/api/series/mangaka/**", "/api/series/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/series/create").hasRole("ADMIN")
                        .requestMatchers("/api/account/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                        .accessDeniedHandler(SecurityConfig::handleAccessDenied))
                .addFilterBefore(sessionUserAuthenticationFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .build();
    }

    /**
     * 403 cho fetch/AJAX trả JSON {status, message} (giao diện đọc được và báo cho người dùng)
     * thay vì trang HTML lỗi; trình duyệt điều hướng bình thường (Accept: text/html) vẫn nhận 403 mặc định.
     */
    private static void handleAccessDenied(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) throws IOException {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String message = ex instanceof CsrfException
                ? "Phiên làm việc đã hết hạn hoặc thiếu mã bảo mật. Hãy tải lại trang rồi thử lại."
                : "Bạn không có quyền thực hiện thao tác này.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
    }
}
