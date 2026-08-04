package com.aieducenter.admin.application.dto.wire;

import java.util.List;
import java.util.Set;

/**
 * app-registry 创建/更新 SSO 客户端的 wire 请求体。
 *
 * @since 0.1.0
 */
public record CreateSsoClientWireRequest(
        List<String> redirectUris,
        Set<String> scopes,
        Set<String> grants
) {}
