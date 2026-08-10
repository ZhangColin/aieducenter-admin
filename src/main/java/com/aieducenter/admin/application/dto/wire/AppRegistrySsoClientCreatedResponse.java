package com.aieducenter.admin.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * app-registry {@code SsoClientCreatedResponse} 的 wire 镜像——含一次性明文 {@code clientSecret}。
 *
 * <p>仅凭证接口（开通/重置 {@code client_secret}，{@code client_id} 终身稳定）时返回，消费方须立即捕获 secret。
 * 之后 GET 仅返 {@link AppRegistrySsoClientResponse}（无 secret）。</p>
 *
 * @since 0.1.0
 */
public record AppRegistrySsoClientCreatedResponse(
        Long id,
        Long appId,
        String clientId,
        String clientSecret,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> grants,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
