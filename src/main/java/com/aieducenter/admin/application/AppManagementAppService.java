package com.aieducenter.admin.application;

import com.aieducenter.admin.application.dto.query.AppManagementQuery;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryAppResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientResponse;
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
}
