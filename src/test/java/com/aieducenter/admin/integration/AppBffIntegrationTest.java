package com.aieducenter.admin.integration;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryAppResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientResponse;
import com.aieducenter.admin.infrastructure.AppRegistryClient;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 应用管理 BFF 集成测试——mock {@link AppRegistryClient}，验证 AppService 在 Spring
 * 上下文中的完整接线（DI、DTO 映射、异常转译）。
 *
 * <p>不模拟安全层（权限在单元层 {@code AppControllerTest} 覆盖）。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppBffIntegrationTest {

    @Autowired
    private AppManagementAppService appService;

    @MockBean
    private AppRegistryClient appRegistryClient;

    // ========== list ==========

    @Test
    void given_keyword_when_list_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.listApps(eq("foo"), eq(null), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new AppRegistryAppResponse(1L, "foo", "Foo App", "desc",
                                1, "启用", now, now),
                        new AppRegistryAppResponse(2L, "foobar", "Foobar", "",
                                0, "禁用", now, now)
                ), 2L, 1, 20));

        var page = appService.list(
                new com.aieducenter.admin.application.dto.query.AppManagementQuery("foo", null),
                PageRequest.of(0, 20));

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).name()).isEqualTo("Foo App");
        assertThat(page.items().get(1).statusName()).isEqualTo("禁用");
    }

    // ========== getDetail ==========

    @Test
    void given_appWithAllThreeAggregates_when_getDetail_then_assembleAll() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.getApp(1L)).thenReturn(
                new AppRegistryAppResponse(1L, "full-app", "Full App", "desc",
                        1, "启用", now, now));
        when(appRegistryClient.getApiKey(1L)).thenReturn(Optional.of(
                new AppRegistryApiKeyResponse(10L, 1L, "full-app", 1, "启用", now, now)));
        when(appRegistryClient.getSsoClient(1L)).thenReturn(Optional.of(
                new AppRegistrySsoClientResponse(20L, 1L, "oidc-1",
                        List.of("https://cb.example.com"), Set.of("openid", "profile"),
                        Set.of("authorization_code"), 1, "启用", now, now)));

        AppDetailResponse detail = appService.getDetail(1L);

        assertThat(detail.appCode()).isEqualTo("full-app");
        assertThat(detail.apiKey().apiKey()).isEqualTo("full-app");
        assertThat(detail.ssoClient().clientId()).isEqualTo("oidc-1");
        assertThat(detail.ssoClient().scopes()).containsExactlyInAnyOrder("openid", "profile");
    }

    @Test
    void given_appWithoutSsoClient_when_getDetail_then_ssoClientNull() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.getApp(1L)).thenReturn(
                new AppRegistryAppResponse(1L, "no-sso", "No SSO", "", 1, "启用", now, now));
        when(appRegistryClient.getApiKey(1L)).thenReturn(Optional.of(
                new AppRegistryApiKeyResponse(10L, 1L, "no-sso", 1, "启用", now, now)));
        when(appRegistryClient.getSsoClient(1L)).thenReturn(Optional.empty());

        AppDetailResponse detail = appService.getDetail(1L);

        assertThat(detail.appCode()).isEqualTo("no-sso");
        assertThat(detail.apiKey()).isNotNull();
        assertThat(detail.ssoClient()).isNull();
    }

    @Test
    void given_appRegistry404_when_getDetail_then_throwDomainException() {
        when(appRegistryClient.getApp(99L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> appService.getDetail(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }
}
