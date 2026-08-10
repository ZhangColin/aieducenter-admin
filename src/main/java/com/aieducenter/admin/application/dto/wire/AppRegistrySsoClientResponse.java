package com.aieducenter.admin.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * app-registry {@code SsoClientResponse} 的 wire 镜像——不含 clientSecret。
 *
 * @since 0.1.0
 */
public record AppRegistrySsoClientResponse(
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
