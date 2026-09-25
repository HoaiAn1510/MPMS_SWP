package com.example.manga_management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

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
                .csrf(AbstractHttpConfigurer::disable)
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
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/login")))
                .addFilterBefore(sessionUserAuthenticationFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .build();
    }
}
