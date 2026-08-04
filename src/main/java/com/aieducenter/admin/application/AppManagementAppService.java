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
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 应用管理 BFF 应用服务——聚合 app-registry 的 app + apiKey + ssoClient 三元组。
 *
 * @since 0.1.0
 */
@Service
public class AppManagementAppService {

    private static final Logger log = LoggerFactory.getLogger(AppManagementAppService.class);

    private final AppRegistryClient appRegistryClient;

    public AppManagementAppService(AppRegistryClient appRegistryClient) {
        this.appRegistryClient = appRegistryClient;
    }

    /**
     * 分页查询应用列表（透传 app-registry）。
     */
    public PageResponse<AppSummaryResponse> list(AppManagementQuery query, Pageable pageable) {
        PageResponse<AppRegistryAppResponse> page = appRegistryClient.listApps(
                query.keyword(), query.status(),
                pageable.getPageNumber() + 1,  // Spring Pageable 是 0-based，app-registry 用 1-based
                pageable.getPageSize());

        var items = page.items().stream()
                .map(AppManagementAppService::toSummary)
                .toList();

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    /**
     * 查询应用详情——聚合 app + apiKey + ssoClient。
     *
     * <p>app-registry 404 透传为 {@link DomainException}；ssoClient 为 null 正常（非全部应用配 SSO）。</p>
     */
    public AppDetailResponse getDetail(Long id) {
        AppRegistryAppResponse app;
        try {
            app = appRegistryClient.getApp(id);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, id);
            }
            throw e;
        }

        Optional<AppRegistryApiKeyResponse> apiKey = appRegistryClient.getApiKey(id);
        Optional<AppRegistrySsoClientResponse> ssoClient = appRegistryClient.getSsoClient(id);

        return toDetail(app, apiKey.orElse(null), ssoClient.orElse(null));
    }

    /**
     * 创建应用——透传 appCode + name + description 至 app-registry。
     *
     * <p>新创建的应用尚无 apiKey/ssoClient，响应中对应字段为 null。</p>
     */
    public AppDetailResponse create(CreateAppCommand command) {
        var wireRequest = new CreateAppWireRequest(command.appCode(), command.name(), command.description());
        AppRegistryAppResponse app;
        try {
            app = appRegistryClient.createApp(wireRequest);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, command.appCode());
            }
            if (e.getStatusCode() == 409) {
                throw new DomainException(BaseCodeMessage.CONFLICT, command.appCode());
            }
            throw e;
        }
        log.info("Created app: id={}, appCode={}", app.id(), app.appCode());
        return toDetail(app, null, null);
    }

    /**
     * 更新应用——仅 name/description 可改，appCode 不可变。
     */
    public AppDetailResponse update(Long id, UpdateAppCommand command) {
        var wireRequest = new UpdateAppWireRequest(command.name(), command.description());
        AppRegistryAppResponse app;
        try {
            app = appRegistryClient.updateApp(id, wireRequest);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, id);
            }
            if (e.getStatusCode() == 409) {
                throw new DomainException(BaseCodeMessage.CONFLICT, id);
            }
            throw e;
        }
        log.info("Updated app: id={}, appCode={}", app.id(), app.appCode());
        return toDetail(app, null, null);
    }

    /**
     * 停用应用——已停用时返回 409。
     */
    public void disable(Long id) {
        try {
            appRegistryClient.disableApp(id);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, id);
            }
            if (e.getStatusCode() == 409) {
                throw new DomainException(BaseCodeMessage.CONFLICT, id);
            }
            throw e;
        }
        log.info("Disabled app: id={}", id);
    }

    /**
     * 启用应用——已启用时返回 409。
     */
    public void enable(Long id) {
        try {
            appRegistryClient.enableApp(id);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, id);
            }
            if (e.getStatusCode() == 409) {
                throw new DomainException(BaseCodeMessage.CONFLICT, id);
            }
            throw e;
        }
        log.info("Enabled app: id={}", id);
    }

    /**
     * 生成/重置 ApiKey——无则生成、有则重置，返回含明文 apiSecret 的一次性响应。
     *
     * <p>app-registry 404 透传为 {@link DomainException}。</p>
     */
    public ApiKeyCreatedResponse manageApiKey(Long appId) {
        AppRegistryApiKeyCreatedResponse wire;
        try {
            wire = appRegistryClient.createOrRotateApiKey(appId);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, appId);
            }
            throw e;
        }
        log.info("Managed ApiKey: appId={}, apiKey={}", appId, wire.apiKey());
        return toApiKeyCreated(wire);
    }

    /**
     * 创建/更新 SSO 客户端——返回含明文 clientSecret 的一次性响应。
     *
     * <p>app-registry 404 透传为 {@link DomainException}；校验（如 redirectUris 为空）由 app-registry 负责，
     * 错误通过 {@link OpenApiClientException} 透传。</p>
     */
    public SsoClientCreatedResponse manageSsoClient(Long appId, ManageSsoClientCommand command) {
        var wireRequest = new CreateSsoClientWireRequest(
                command.redirectUris(), command.scopes(), command.grants());
        AppRegistrySsoClientCreatedResponse wire;
        try {
            wire = appRegistryClient.createOrUpdateSsoClient(appId, wireRequest);
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                throw new DomainException(BaseCodeMessage.NOT_FOUND, appId);
            }
            throw e;
        }
        log.info("Managed SsoClient: appId={}, clientId={}", appId, wire.clientId());
        return toSsoClientCreated(wire);
    }

    // ========== 映射方法 ==========

    private static AppSummaryResponse toSummary(AppRegistryAppResponse wire) {
        return new AppSummaryResponse(
                wire.id(), wire.appCode(), wire.name(), wire.description(),
                wire.status(), wire.statusName(),
                wire.createdAt(), wire.updatedAt());
    }

    private static AppDetailResponse toDetail(AppRegistryAppResponse app,
                                              AppRegistryApiKeyResponse apiKey,
                                              AppRegistrySsoClientResponse ssoClient) {
        AppDetailResponse.ApiKeyInfo apiKeyInfo = apiKey != null
                ? new AppDetailResponse.ApiKeyInfo(
                        apiKey.id(), apiKey.apiKey(), apiKey.status(), apiKey.statusName(),
                        apiKey.createdAt(), apiKey.updatedAt())
                : null;

        AppDetailResponse.SsoClientInfo ssoClientInfo = ssoClient != null
                ? new AppDetailResponse.SsoClientInfo(
                        ssoClient.id(), ssoClient.clientId(),
                        ssoClient.redirectUris(), ssoClient.scopes(), ssoClient.grants(),
                        ssoClient.status(), ssoClient.statusName(),
                        ssoClient.createdAt(), ssoClient.updatedAt())
                : null;

        return new AppDetailResponse(
                app.id(), app.appCode(), app.name(), app.description(),
                app.status(), app.statusName(),
                apiKeyInfo, ssoClientInfo,
                app.createdAt(), app.updatedAt());
    }

    private static ApiKeyCreatedResponse toApiKeyCreated(AppRegistryApiKeyCreatedResponse wire) {
        return new ApiKeyCreatedResponse(
                wire.id(), wire.appId(), wire.apiKey(), wire.apiSecret(),
                wire.status(), wire.statusName(),
                wire.createdAt(), wire.updatedAt());
    }

    private static SsoClientCreatedResponse toSsoClientCreated(AppRegistrySsoClientCreatedResponse wire) {
        return new SsoClientCreatedResponse(
                wire.id(), wire.appId(), wire.clientId(), wire.clientSecret(),
                wire.redirectUris(), wire.scopes(), wire.grants(),
                wire.status(), wire.statusName(),
                wire.createdAt(), wire.updatedAt());
    }
}
