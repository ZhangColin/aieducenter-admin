package com.aieducenter.admin.application.dto.wire;

/**
 * app-registry 创建应用的 wire 请求体。
 *
 * @since 0.1.0
 */
public record CreateAppWireRequest(
        String appCode,
        String name,
        String description
) {}
