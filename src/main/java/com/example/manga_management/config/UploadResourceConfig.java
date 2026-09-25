package com.example.manga_management.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final Path bookJacketDirectory;

    public UploadResourceConfig(@Value("${app.upload.root:uploads}") String uploadRoot) {
        this.bookJacketDirectory = Path.of(uploadRoot)
                .toAbsolutePath()
                .normalize()
                .resolve("bookjackets");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String externalLocation = bookJacketDirectory.toUri().toString();
        if (!externalLocation.endsWith("/")) {
            externalLocation += "/";
        }
        registry.addResourceHandler("/bookjackets/**")
                .addResourceLocations(
                        externalLocation,
                        "classpath:/static/bookjackets/")
                .setCacheControl(CacheControl.noCache());
    }
}
