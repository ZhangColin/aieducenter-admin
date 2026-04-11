package com.aieducenter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置类
 *
 * <p>配置 SpringDoc 自动生成的 OpenAPI 文档，包括：
 * <ul>
 *   <li>API 元信息（标题、版本、描述）</li>
 *   <li>Bearer Token 认证方式</li>
 * </ul>
 *
 * <p>文档访问地址：
 * <ul>
 *   <li>Swagger UI: http://localhost:8081/swagger-ui.html</li>
 *   <li>OpenAPI JSON: http://localhost:8081/v3/api-docs</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("管理后台 API")
                        .version("1.0.0")
                        .description("海创元智研云平台 - 管理后台 API 文档"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .name("BearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 认证，格式：Bearer {token}")));
    }
}
