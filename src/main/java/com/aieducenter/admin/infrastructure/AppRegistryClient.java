package com.aieducenter.admin.infrastructure;

import com.aieducenter.admin.application.dto.wire.AppRegistryApiKeyResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistryAppResponse;
import com.aieducenter.admin.application.dto.wire.AppRegistrySsoClientResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * app-registry 签名 HTTP 客户端——封装 {@link OpenApiClient}，屏蔽 wire 层细节。
 *
 * <p>所有对 app-registry 的调用都经此客户端发起，自动签名（admin 自身 apiKey/admin-console）。</p>
 *
 * @since 0.1.0
 */
@Component
public class AppRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(AppRegistryClient.class);

    private static final TypeReference<PageResponse<AppRegistryAppResponse>> APP_PAGE_TYPEREF =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<AppRegistryAppResponse>> APP_DETAIL_TYPEREF =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<AppRegistryApiKeyResponse>> APIKEY_TYPEREF =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<AppRegistrySsoClientResponse>> SSO_CLIENT_TYPEREF =
            new TypeReference<>() {};

    private final OpenApiClient openApiClient;
    private final String baseUrl;

    public AppRegistryClient(OpenApiClient openApiClient,
                             @Value("${admin.app-registry.base-url}") String baseUrl) {
        this.openApiClient = openApiClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 分页查询应用列表。
     */
    public PageResponse<AppRegistryAppResponse> listApps(String keyword, Integer status, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/app-registry/apps?page=").append(page - 1)  // 入参是 1-based（AppService 传入），app-registry 的 Spring Pageable 用 0-based
                .append("&size=").append(size);
        if (keyword != null && !keyword.isEmpty()) {
            url.append("&keyword=").append(encode(keyword));
        }
        if (status != null) {
            url.append("&status=").append(status);
        }
        log.debug("AppRegistryClient.listApps: {}", url);
        return openApiClient.get(url.toString(), APP_PAGE_TYPEREF);
    }

    /**
     * 查询应用详情。
     *
     * @throws OpenApiClientException 404 时透传
     */
    public AppRegistryAppResponse getApp(Long id) {
        String url = baseUrl + "/api/app-registry/apps/" + id;
        log.debug("AppRegistryClient.getApp: {}", url);
        ApiResponse<AppRegistryAppResponse> resp = openApiClient.get(url, APP_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 查询应用的 ApiKey——可能不存在。
     */
    public Optional<AppRegistryApiKeyResponse> getApiKey(Long appId) {
        String url = baseUrl + "/api/app-registry/apps/" + appId + "/api-keys";
        log.debug("AppRegistryClient.getApiKey: {}", url);
        try {
            ApiResponse<AppRegistryApiKeyResponse> resp = openApiClient.get(url, APIKEY_TYPEREF);
            return Optional.ofNullable(resp.data());
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * 查询应用的 SsoClient——不一定每个应用都配 SSO。
     */
    public Optional<AppRegistrySsoClientResponse> getSsoClient(Long appId) {
        String url = baseUrl + "/api/app-registry/apps/" + appId + "/sso-clients";
        log.debug("AppRegistryClient.getSsoClient: {}", url);
        try {
            ApiResponse<AppRegistrySsoClientResponse> resp = openApiClient.get(url, SSO_CLIENT_TYPEREF);
            return Optional.ofNullable(resp.data());
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static String encode(String value) {
        // 简单 URL 编码，避免特殊字符问题
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
