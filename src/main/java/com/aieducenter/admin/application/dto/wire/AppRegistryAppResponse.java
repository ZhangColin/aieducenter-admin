package com.aieducenter.admin.application.dto.wire;

import java.time.LocalDateTime;

/**
 * app-registry {@code AppResponse} 的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 OpenApiClient 响应，不与 app-registry 内部 DTO 耦合。</p>
 *
 * @since 0.1.0
 */
public record AppRegistryAppResponse(
        Long id,
        String appCode,
        String name,
        String description,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
