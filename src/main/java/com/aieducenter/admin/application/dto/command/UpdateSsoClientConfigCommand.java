package com.aieducenter.admin.application.dto.command;

import java.util.List;
import java.util.Set;

/**
 * 更新 SsoClient 配置命令——整份替换 {@code redirectUris} / {@code postLogoutRedirectUris} /
 * {@code scopes} / {@code grants} 四件套。
 *
 * <p>凭证与配置分离（[ADR-0006](../../../../../../../docs/adr/0006-sso-client-bff-mirrors-credential-config-split.md)）：
 * 本命令只动配置、<strong>不动</strong> {@code client_id} / {@code client_secret} / status。admin 作为 BFF 纯透传，
 * 不加 {@code @NotEmpty} 等校验——空列表等场景由 app-registry 校验后透传（admin 不做业务判断）。</p>
 *
 * @since 0.1.0
 */
public record UpdateSsoClientConfigCommand(

        List<String> redirectUris,

        List<String> postLogoutRedirectUris,

        Set<String> scopes,

        Set<String> grants

) {}
