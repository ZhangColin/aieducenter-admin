package com.aieducenter.admin.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 应用详情响应——聚合 app + apiKey + ssoClient 三元组。
 *
 * <p>apiSecret / clientSecret 从不出站。ssoClient 为 null 表示该应用未配置 SSO。</p>
 *
 * @since 0.1.0
 */
public record AppDetailResponse(
        Long id,
        String appCode,
        String name,
        String description,
        Integer status,
        String statusName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiKeyInfo apiKey,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        SsoClientInfo ssoClient,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * ApiKey 信息——不含 apiSecret。
     */
    public record ApiKeyInfo(
            Long id,
            String apiKey,
            Integer status,
            String statusName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * SsoClient 信息——不含 clientSecret。
     */
    public record SsoClientInfo(
            Long id,
            String clientId,
            List<String> redirectUris,
            Set<String> scopes,
            Set<String> grants,
            Integer status,
            String statusName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
