package com.aieducenter.admin.application.dto.command;

import java.util.List;
import java.util.Set;

/**
 * 管理 SSO 客户端命令——创建或更新 OIDC SSO 配置。
 *
 * <p>校验（如 redirectUris 为空）由 app-registry 负责，admin 不做业务判断，
 * 错误通过 {@code OpenApiClientException} 透传。</p>
 *
 * @param redirectUris 回调地址列表
 * @param scopes       授权范围（可空）
 * @param grants       授权类型（可空）
 * @since 0.1.0
 */
public record ManageSsoClientCommand(
        List<String> redirectUris,
        Set<String> scopes,
        Set<String> grants
) {}
