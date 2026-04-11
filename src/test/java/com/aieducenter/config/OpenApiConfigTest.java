package com.aieducenter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenApiConfig 集成测试。
 *
 * <p>验证 OpenAPI Bean 的配置是否正确
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OpenApiConfig.class)
class OpenApiConfigTest {

    @Autowired
    private OpenAPI openAPI;

    @Test
    void shouldConfigureOpenAPI() {
        assertThat(openAPI).isNotNull();
    }

    @Test
    void shouldHaveCorrectInfo() {
        assertThat(openAPI.getInfo().getTitle())
                .isEqualTo("海创元智研云平台 API");
        assertThat(openAPI.getInfo().getVersion())
                .isEqualTo("1.0.0");
        assertThat(openAPI.getInfo().getDescription())
                .isEqualTo("AI 聚合 SaaS 平台接口");
    }

    @Test
    void shouldHaveBearerAuthSecurityScheme() {
        SecurityScheme bearerAuth = openAPI.getComponents()
                .getSecuritySchemes()
                .get("BearerAuth");

        assertThat(bearerAuth).isNotNull();
        assertThat(bearerAuth.getType())
                .isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme())
                .isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat())
                .isEqualTo("JWT");
    }

    @Test
    void shouldHaveSecurityRequirement() {
        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().get(0))
                .containsKey("BearerAuth");
    }
}
