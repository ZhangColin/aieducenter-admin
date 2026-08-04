package com.aieducenter.admin.application.dto.wire;

/**
 * app-registry 更新应用的 wire 请求体——appCode 不可修改。
 *
 * @since 0.1.0
 */
public record UpdateAppWireRequest(
        String name,
        String description
) {}
