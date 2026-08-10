package com.aieducenter.admin.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SsoClient 配置视图——配置 PUT 的响应，<strong>不含</strong> {@code clientSecret}。
 *
 * <p>凭证与配置分离（[ADR-0006](../../../../../../../docs/adr/0006-sso-client-bff-mirrors-credential-config-split.md)）：
 * 一次性明文 {@code clientSecret} 仅凭证接口返回；配置 PUT 整份替换配置后返回此视图。
 * {@code client_id} / status 不动，原样回显；配置四件套（含 {@code postLogoutRedirectUris}）入参与响应往返一致。</p>
 *
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SsoClientResponse(
        Long id,
        Long appId,
        String clientId,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> grants,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
