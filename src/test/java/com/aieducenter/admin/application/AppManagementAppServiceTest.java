package com.aieducenter.admin.application;

import com.aieducenter.admin.application.dto.query.AppManagementQuery;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryAppResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientResponse;
import com.aieducenter.admin.infrastructure.AppRegistryClient;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppManagementAppService 单元测试——mock AppRegistryClient。
 */
@ExtendWith(MockitoExtension.class)
class AppManagementAppServiceTest {

    @Mock
    private AppRegistryClient appRegistryClient;

    private AppManagementAppService service;

    @BeforeEach
    void setUp() {
        service = new AppManagementAppService(appRegistryClient);
    }

    // ========== list ==========

    @Test
    void given_keywordAndStatus_when_list_then_delegateToClientAndMap() {
        var wire = new AppRegistryAppResponse(1L, "test-app", "Test", "desc",
                1, "启用", LocalDateTime.now(), LocalDateTime.now());
        when(appRegistryClient.listApps(eq("test"), eq(1), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(wire), 1L, 1, 20));

        PageResponse<AppSummaryResponse> result = service.list(
                new AppManagementQuery("test", 1), PageRequest.of(0, 20));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        AppSummaryResponse item = result.items().get(0);
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.appCode()).isEqualTo("test-app");
        assertThat(item.name()).isEqualTo("Test");
        // 确认不含 apiSecret/clientSecret（wire 层本就不含）
        verify(appRegistryClient).listApps(eq("test"), eq(1), anyInt(), anyInt());
    }

    @Test
    void given_nullKeywordAndStatus_when_list_then_delegateWithNulls() {
        when(appRegistryClient.listApps(eq(null), eq(null), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 1, 20));

        PageResponse<AppSummaryResponse> result = service.list(
                new AppManagementQuery(null, null), PageRequest.of(0, 20));

        assertThat(result.total()).isEqualTo(0L);
    }

    // ========== getDetail ==========

    @Test
    void given_appWithApiKeyAndSsoClient_when_getDetail_then_assembleAll() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.getApp(1L)).thenReturn(
                new AppRegistryAppResponse(1L, "my-app", "My App", "desc", 1, "启用", now, now));
        when(appRegistryClient.getApiKey(1L)).thenReturn(Optional.of(
                new AppRegistryApiKeyResponse(10L, 1L, "my-app", 1, "启用", now, now)));
        when(appRegistryClient.getSsoClient(1L)).thenReturn(Optional.of(
                new AppRegistrySsoClientResponse(20L, 1L, "oidc-client",
                        List.of("https://example.com/callback"), Set.of("openid"), Set.of("authorization_code"),
                        1, "启用", now, now)));

        AppDetailResponse detail = service.getDetail(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.appCode()).isEqualTo("my-app");
        assertThat(detail.apiKey()).isNotNull();
        assertThat(detail.apiKey().apiKey()).isEqualTo("my-app");
        assertThat(detail.ssoClient()).isNotNull();
        assertThat(detail.ssoClient().clientId()).isEqualTo("oidc-client");
        assertThat(detail.ssoClient().redirectUris()).containsExactly("https://example.com/callback");
    }

    @Test
    void given_appWithoutSsoClient_when_getDetail_then_ssoClientNull() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.getApp(1L)).thenReturn(
                new AppRegistryAppResponse(1L, "no-sso", "No SSO", "desc", 1, "启用", now, now));
        when(appRegistryClient.getApiKey(1L)).thenReturn(Optional.of(
                new AppRegistryApiKeyResponse(10L, 1L, "no-sso", 1, "启用", now, now)));
        when(appRegistryClient.getSsoClient(1L)).thenReturn(Optional.empty());

        AppDetailResponse detail = service.getDetail(1L);

        assertThat(detail.appCode()).isEqualTo("no-sso");
        assertThat(detail.apiKey()).isNotNull();
        assertThat(detail.ssoClient()).isNull();  // 正常：非全部应用配 SSO
    }

    @Test
    void given_appNotFound_when_getDetail_then_throwDomainException404() {
        when(appRegistryClient.getApp(99L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    @Test
    void given_appRegistry500_when_getDetail_then_propagateOpenApiClientException() {
        when(appRegistryClient.getApp(1L))
                .thenThrow(new OpenApiClientException(500, "Internal Server Error"));

        assertThatThrownBy(() -> service.getDetail(1L))
                .isInstanceOf(OpenApiClientException.class)
                .matches(e -> ((OpenApiClientException) e).getStatusCode() == 500);
    }
}
