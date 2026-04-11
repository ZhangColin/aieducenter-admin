package com.aieducenter.config;

import io.swagger.v3.oas.models.OpenAPI;
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
                .isEqualTo("管理后台 API");
        assertThat(openAPI.getInfo().getVersion())
                .isEqualTo("1.0.0");
        assertThat(openAPI.getInfo().getDescription())
                .isEqualTo("海创元智研云平台 - 管理后台 API 文档");
    }

    @Test
    void shouldHaveBearerAuthSecurityScheme() {
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("BearerAuth");
        var scheme = openAPI.getComponents().getSecuritySchemes().get("BearerAuth");
        assertThat(scheme.getType()).isEqualTo(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
    }

    @Test
    void shouldHaveSecurityRequirement() {
        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().getFirst().keySet()).contains("BearerAuth");
    }
}
