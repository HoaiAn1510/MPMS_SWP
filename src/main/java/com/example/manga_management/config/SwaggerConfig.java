package com.example.manga_management.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI mangaManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Manga Publication Management System API")
                        .description("API documentation for the MPMS (Manga Publication Management System) — "
                                + "covers Mangaka submissions, Tantou reviews, Board voting, ranking, "
                                + "drawing tools, and AI-assisted features.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MPMS Team")
                                .email("mpms-support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
