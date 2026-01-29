package com.aigen.studio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aigenStudioOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AIGen Studio API")
                .description("AI Code Generation Platform API")
                .version("v1.0.0")
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")));
    }
}