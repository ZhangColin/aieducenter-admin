package com.aieducenter.admin.application.dto.wire;

import java.time.LocalDateTime;

/**
 * app-registry {@code ApiKeyResponse} 的 wire 镜像——不含 apiSecret。
 *
 * @since 0.1.0
 */
public record AppRegistryApiKeyResponse(
        Long id,
        Long appId,
        String apiKey,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
