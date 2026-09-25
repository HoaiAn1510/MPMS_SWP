package com.example.manga_management.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.manga_management.service.DataAccessService;
import com.example.manga_management.service.FileStorageService;

/**
 * Phục vụ file upload từ {@code app.upload.root} với URL giữ nguyên như cũ
 * (vd /MangaPage/PG00001.png). Có fallback {@code classpath:/static/} cho dữ
 * liệu seed cũ. Quyền truy cập do SecurityConfig quyết định (bookjackets công
 * khai, các thư mục còn lại yêu cầu đăng nhập).
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private static final String BOOK_JACKET_DIR = "bookjackets";

    private final Path uploadRoot;
    private final DataAccessService dataAccessService;

    public UploadResourceConfig(@Value("${app.upload.root:uploads}") String uploadRoot,
            DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
        this.uploadRoot = Path.of(uploadRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> dirs = new java.util.ArrayList<>(FileStorageService.ALLOWED_DIRS);
        dirs.add(BOOK_JACKET_DIR);
        for (String dir : dirs) {
            registry.addResourceHandler("/" + dir + "/**")
                    .addResourceLocations(
                            externalLocation(dir),
                            "classpath:/static/" + dir + "/")
                    .setCacheControl(CacheControl.noCache());
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // File nhạy cảm: chỉ vai trò liên quan tới dữ liệu mới tải được.
        registry.addInterceptor(new UploadAccessInterceptor(dataAccessService))
                .addPathPatterns("/proposal/**", "/series-defense/**", "/tantou-profile/**",
                        "/MangaPage/**", "/Submission/**");
    }

    private String externalLocation(String dir) {
        String location = uploadRoot.resolve(dir).toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
