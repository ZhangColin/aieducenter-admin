package com.aieducenter.admin.application.dto.wire;

import java.time.LocalDateTime;

/**
 * app-registry {@code ApiKeyCreatedResponse} 的 wire 镜像——含一次性明文 {@code apiSecret}。
 *
 * <p>仅 create-or-rotate 时返回，消费方须立即捕获 secret。之后 GET 仅返 {@link AppRegistryApiKeyResponse}（无 secret）。</p>
 *
 * @since 0.1.0
 */
public record AppRegistryApiKeyCreatedResponse(
        Long id,
        Long appId,
        String apiKey,
        String apiSecret,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
