package com.aieducenter.admin.application;

import com.aieducenter.admin.application.dto.command.CreateAppCommand;
import com.aieducenter.admin.application.dto.command.ManageSsoClientCommand;
import com.aieducenter.admin.application.dto.command.UpdateAppCommand;
import com.aieducenter.admin.application.dto.query.AppManagementQuery;
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
import com.aieducenter.admin.application.dto.wire.CreateSsoClientWireRequest;
import com.aieducenter.admin.application.dto.wire.UpdateAppWireRequest;
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
import static org.mockito.Mockito.doThrow;
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

    // ========== create ==========

    @Test
    void given_validCommand_when_create_then_delegateToClientAndReturnDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createApp(any(CreateAppWireRequest.class)))
                .thenReturn(new AppRegistryAppResponse(1L, "new-app", "New App", "desc",
                        1, "启用", now, now));

        AppDetailResponse result = service.create(
                new CreateAppCommand("new-app", "New App", "desc"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.appCode()).isEqualTo("new-app");
        assertThat(result.name()).isEqualTo("New App");
        assertThat(result.apiKey()).isNull();   // 新应用尚无 apiKey
        assertThat(result.ssoClient()).isNull();
        verify(appRegistryClient).createApp(any(CreateAppWireRequest.class));
    }

    @Test
    void given_appRegistry404_when_create_then_throwDomainException() {
        when(appRegistryClient.createApp(any(CreateAppWireRequest.class)))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.create(
                new CreateAppCommand("bad", "Bad", "")))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    @Test
    void given_appRegistry409_when_create_then_throwDomainException() {
        when(appRegistryClient.createApp(any(CreateAppWireRequest.class)))
                .thenThrow(new OpenApiClientException(409, "{\"message\":\"Conflict\"}"));

        assertThatThrownBy(() -> service.create(
                new CreateAppCommand("dup", "Dup", "")))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 409);
    }

    // ========== update ==========

    @Test
    void given_validCommand_when_update_then_delegateToClientAndReturnDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.updateApp(eq(1L), any(UpdateAppWireRequest.class)))
                .thenReturn(new AppRegistryAppResponse(1L, "my-app", "Updated", "new-desc",
                        1, "启用", now, now));

        AppDetailResponse result = service.update(1L,
                new UpdateAppCommand("Updated", "new-desc"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Updated");
        assertThat(result.description()).isEqualTo("new-desc");
        verify(appRegistryClient).updateApp(eq(1L), any(UpdateAppWireRequest.class));
    }

    @Test
    void given_appRegistry404_when_update_then_throwDomainException() {
        when(appRegistryClient.updateApp(eq(99L), any(UpdateAppWireRequest.class)))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.update(99L,
                new UpdateAppCommand("X", "")))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    @Test
    void given_appRegistry409_when_update_then_throwDomainException() {
        when(appRegistryClient.updateApp(eq(1L), any(UpdateAppWireRequest.class)))
                .thenThrow(new OpenApiClientException(409, "{\"message\":\"Conflict\"}"));

        assertThatThrownBy(() -> service.update(1L,
                new UpdateAppCommand("X", "")))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 409);
    }

    // ========== disable ==========

    @Test
    void given_validId_when_disable_then_delegateToClient() {
        service.disable(1L);
        verify(appRegistryClient).disableApp(1L);
    }

    @Test
    void given_appRegistry404_when_disable_then_throwDomainException() {
        doThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"))
                .when(appRegistryClient).disableApp(99L);

        assertThatThrownBy(() -> service.disable(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    @Test
    void given_appRegistry409_when_disable_then_throwDomainException() {
        doThrow(new OpenApiClientException(409, "{\"message\":\"Already disabled\"}"))
                .when(appRegistryClient).disableApp(1L);

        assertThatThrownBy(() -> service.disable(1L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 409);
    }

    // ========== enable ==========

    @Test
    void given_validId_when_enable_then_delegateToClient() {
        service.enable(1L);
        verify(appRegistryClient).enableApp(1L);
    }

    @Test
    void given_appRegistry404_when_enable_then_throwDomainException() {
        doThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"))
                .when(appRegistryClient).enableApp(99L);

        assertThatThrownBy(() -> service.enable(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    @Test
    void given_appRegistry409_when_enable_then_throwDomainException() {
        doThrow(new OpenApiClientException(409, "{\"message\":\"Already enabled\"}"))
                .when(appRegistryClient).enableApp(1L);

        assertThatThrownBy(() -> service.enable(1L))
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

        ApiKeyCreatedResponse result = service.manageApiKey(1L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.apiKey()).isEqualTo("my-app");
        assertThat(result.apiSecret()).isEqualTo("sk-abc123");
        assertThat(result.status()).isEqualTo(1);
        verify(appRegistryClient).createOrRotateApiKey(1L);
    }

    @Test
    void given_existingApiKey_when_manageApiKey_then_rotateAndReturnNewSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrRotateApiKey(1L))
                .thenReturn(new AppRegistryApiKeyCreatedResponse(10L, 1L, "my-app", "sk-new-secret",
                        1, "启用", now, now));

        ApiKeyCreatedResponse result = service.manageApiKey(1L);

        assertThat(result.apiSecret()).isEqualTo("sk-new-secret");
        verify(appRegistryClient).createOrRotateApiKey(1L);
    }

    @Test
    void given_appNotFound_when_manageApiKey_then_throwDomainException404() {
        when(appRegistryClient.createOrRotateApiKey(99L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.manageApiKey(99L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    // ========== manageSsoClient ==========

    @Test
    void given_noExistingSsoClient_when_manageSsoClient_then_createAndReturnSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrUpdateSsoClient(eq(1L), any(CreateSsoClientWireRequest.class)))
                .thenReturn(new AppRegistrySsoClientCreatedResponse(20L, 1L, "oidc-client", "cs-xyz789",
                        List.of("https://example.com/callback"), Set.of("openid"), Set.of("authorization_code"),
                        1, "启用", now, now));

        SsoClientCreatedResponse result = service.manageSsoClient(1L,
                new ManageSsoClientCommand(List.of("https://example.com/callback"), Set.of("openid"), Set.of("authorization_code")));

        assertThat(result.id()).isEqualTo(20L);
        assertThat(result.clientId()).isEqualTo("oidc-client");
        assertThat(result.clientSecret()).isEqualTo("cs-xyz789");
        assertThat(result.redirectUris()).containsExactly("https://example.com/callback");
        assertThat(result.scopes()).containsExactly("openid");
        assertThat(result.grants()).containsExactly("authorization_code");
        assertThat(result.status()).isEqualTo(1);
        verify(appRegistryClient).createOrUpdateSsoClient(eq(1L), any(CreateSsoClientWireRequest.class));
    }

    @Test
    void given_existingSsoClient_when_manageSsoClient_then_updateAndReturnNewSecret() {
        LocalDateTime now = LocalDateTime.now();
        when(appRegistryClient.createOrUpdateSsoClient(eq(1L), any(CreateSsoClientWireRequest.class)))
                .thenReturn(new AppRegistrySsoClientCreatedResponse(20L, 1L, "oidc-client-v2", "cs-new-secret",
                        List.of("https://new.example.com/callback"), Set.of("openid", "profile"), Set.of("authorization_code", "refresh_token"),
                        1, "启用", now, now));

        SsoClientCreatedResponse result = service.manageSsoClient(1L,
                new ManageSsoClientCommand(List.of("https://new.example.com/callback"),
                        Set.of("openid", "profile"), Set.of("authorization_code", "refresh_token")));

        assertThat(result.clientId()).isEqualTo("oidc-client-v2");
        assertThat(result.clientSecret()).isEqualTo("cs-new-secret");
        assertThat(result.redirectUris()).containsExactly("https://new.example.com/callback");
        assertThat(result.scopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(result.grants()).containsExactlyInAnyOrder("authorization_code", "refresh_token");
        verify(appRegistryClient).createOrUpdateSsoClient(eq(1L), any(CreateSsoClientWireRequest.class));
    }

    @Test
    void given_appNotFound_when_manageSsoClient_then_throwDomainException404() {
        when(appRegistryClient.createOrUpdateSsoClient(eq(99L), any(CreateSsoClientWireRequest.class)))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.manageSsoClient(99L,
                new ManageSsoClientCommand(List.of("https://cb.example.com"), Set.of(), Set.of())))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }
}
