package com.chronobeat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chronobeatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chronobeat API")
                        .version("v1")
                        .description("Backend for Chronobeat, a chronological music timeline game. "
                                + "The API is authoritative over game state, song selection, timeline "
                                + "validation and scoring; the Angular client is presentation-only."));
    }
}
