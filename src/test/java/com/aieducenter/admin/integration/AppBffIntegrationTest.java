package com.aieducenter.admin.integration;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateAppCommand;
import com.aieducenter.admin.application.dto.command.UpdateAppCommand;
import com.aieducenter.admin.application.dto.response.ApiKeyCreatedResponse;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.application.dto.response.SsoClientCreatedResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyCreatedResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryAppResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientCreatedResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientResponse;
import com.aieducenter.admin.application.dto.wire.CreateAppWireRequest;
import com.aieducenter.admin.application.dto.wire.UpdateAppWireRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                        List.of("https://cb.example.com"),
                        List.of("https://cb.example.com/logout"),
                        Set.of("openid", "profile"),
                        Set.of("authorization_code"), 1, "启用", now, now)));

        AppDetailResponse detail = appService.getDetail(1L);

        assertThat(detail.appCode()).isEqualTo("full-app");
        assertThat(detail.apiKey().apiKey()).isEqualTo("full-app");
        assertThat(detail.ssoClient().clientId()).isEqualTo("oidc-1");
        assertThat(detail.ssoClient().redirectUris()).containsExactly("https://cb.example.com");
        assertThat(detail.ssoClient().postLogoutRedirectUris()).containsExactly("https://cb.example.com/logout");
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

    // ========== create ==========

    @Test
    void given_validCommand_when_create_then_returnAppDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createApp(any(CreateAppWireRequest.class)))
                .thenReturn(new AppRegistryAppResponse(1L, "new-app", "New App", "desc",
                        1, "启用", now, now));

        AppDetailResponse result = appService.create(
                new CreateAppCommand("new-app", "New App", "desc"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.appCode()).isEqualTo("new-app");
        assertThat(result.name()).isEqualTo("New App");
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.apiKey()).isNull();
        assertThat(result.ssoClient()).isNull();
    }

    // ========== update ==========

    @Test
    void given_validCommand_when_update_then_returnAppDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.updateApp(eq(1L), any(UpdateAppWireRequest.class)))
                .thenReturn(new AppRegistryAppResponse(1L, "my-app", "Updated", "new-desc",
                        1, "启用", now, now));

        AppDetailResponse result = appService.update(1L,
                new UpdateAppCommand("Updated", "new-desc"));

        assertThat(result.name()).isEqualTo("Updated");
        assertThat(result.description()).isEqualTo("new-desc");
        // appCode 不可变
        assertThat(result.appCode()).isEqualTo("my-app");
    }

    // ========== disable ==========

    @Test
    void given_enabledApp_when_disable_then_succeed() {
        appService.disable(1L);
        verify(appRegistryClient).disableApp(1L);
    }

    @Test
    void given_alreadyDisabled_when_disable_then_throw409() {
        doThrow(new OpenApiClientException(409, "{\"message\":\"Already disabled\"}"))
                .when(appRegistryClient).disableApp(1L);

        assertThatThrownBy(() -> appService.disable(1L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 409);
    }

    // ========== enable ==========

    @Test
    void given_disabledApp_when_enable_then_succeed() {
        appService.enable(1L);
        verify(appRegistryClient).enableApp(1L);
    }

    @Test
    void given_alreadyEnabled_when_enable_then_throw409() {
        doThrow(new OpenApiClientException(409, "{\"message\":\"Already enabled\"}"))
                .when(appRegistryClient).enableApp(1L);

        assertThatThrownBy(() -> appService.enable(1L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 409);
    }

    // ========== manageApiKey ==========

    @Test
    void given_noExistingApiKey_when_manageApiKey_then_createAndReturnSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrRotateApiKey(1L))
                .thenReturn(new AppRegistryApiKeyCreatedResponse(10L, 1L, "my-app", "sk-abc123",
                        1, "启用", now, now));

        ApiKeyCreatedResponse result = appService.manageApiKey(1L);

        assertThat(result.apiKey()).isEqualTo("my-app");
        assertThat(result.apiSecret()).isEqualTo("sk-abc123");
        assertThat(result.status()).isEqualTo(1);
        verify(appRegistryClient).createOrRotateApiKey(1L);
    }

    @Test
    void given_existingApiKey_when_manageApiKey_then_rotateAndReturnNewSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrRotateApiKey(1L))
                .thenReturn(new AppRegistryApiKeyCreatedResponse(10L, 1L, "my-app", "sk-rotated",
                        1, "启用", now, now));

        ApiKeyCreatedResponse result = appService.manageApiKey(1L);

        assertThat(result.apiSecret()).isEqualTo("sk-rotated");
        verify(appRegistryClient).createOrRotateApiKey(1L);
    }

    @Test
    void given_appRegistry404_when_manageApiKey_then_throwDomainException() {
        when(appRegistryClient.createOrRotateApiKey(99L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> appService.manageApiKey(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    // ========== manageSsoClientCredentials ==========

    @Test
    void given_noExistingSsoClient_when_manageSsoClientCredentials_then_createAndReturnSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrResetSsoClientCredentials(1L))
                .thenReturn(new AppRegistrySsoClientCreatedResponse(20L, 1L, "oidc-client", "cs-xyz789",
                        List.of(), List.of(), Set.of(), Set.of(),
                        1, "启用", now, now));

        SsoClientCreatedResponse result = appService.manageSsoClientCredentials(1L);

        assertThat(result.clientId()).isEqualTo("oidc-client");
        assertThat(result.clientSecret()).isEqualTo("cs-xyz789");
        // create 时配置四件套全空（含 postLogoutRedirectUris），由配置 PUT 单独编排
        assertThat(result.redirectUris()).isEmpty();
        assertThat(result.postLogoutRedirectUris()).isEmpty();
        assertThat(result.scopes()).isEmpty();
        assertThat(result.grants()).isEmpty();
        verify(appRegistryClient).createOrResetSsoClientCredentials(1L);
    }

    @Test
    void given_existingSsoClient_when_manageSsoClientCredentials_then_resetSecretKeepClientIdStable() {
        LocalDateTime now = LocalDateTime.now();
        // 重置：client_id 终身稳定、client_secret 换新
        when(appRegistryClient.createOrResetSsoClientCredentials(1L))
                .thenReturn(new AppRegistrySsoClientCreatedResponse(20L, 1L, "oidc-stable", "cs-first",
                                List.of("https://example.com/callback"),
                                List.of("https://example.com/logout"),
                                Set.of("openid"), Set.of("authorization_code"),
                                1, "启用", now, now),
                        new AppRegistrySsoClientCreatedResponse(20L, 1L, "oidc-stable", "cs-second",
                                List.of("https://example.com/callback"),
                                List.of("https://example.com/logout"),
                                Set.of("openid"), Set.of("authorization_code"),
                                1, "启用", now, now));

        SsoClientCreatedResponse first = appService.manageSsoClientCredentials(1L);
        SsoClientCreatedResponse second = appService.manageSsoClientCredentials(1L);

        assertThat(second.clientId()).isEqualTo(first.clientId()).isEqualTo("oidc-stable");
        assertThat(first.clientSecret()).isEqualTo("cs-first");
        assertThat(second.clientSecret()).isEqualTo("cs-second").isNotEqualTo(first.clientSecret());
        verify(appRegistryClient, times(2)).createOrResetSsoClientCredentials(1L);
    }

    @Test
    void given_appRegistry404_when_manageSsoClientCredentials_then_throwDomainException() {
        when(appRegistryClient.createOrResetSsoClientCredentials(99L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> appService.manageSsoClientCredentials(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }
}
