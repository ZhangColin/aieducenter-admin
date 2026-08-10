package com.aieducenter.admin.application.dto.wire;

import java.util.List;
import java.util.Set;

/**
 * app-registry {@code PUT .../sso-clients} 的 wire 请求体——整份替换配置四件套。
 *
 * <p>对应下游 [app-registry ADR-0005](../../../../../../../aieducenter-app-registry/docs/adr/0005-sso-client-config-credential-separation.md)：
 * 不动凭证、不动 status；两 URI 列表 {@code @NotEmpty} 校验在下游做，admin 透传。</p>
 *
 * @since 0.1.0
 */
public record UpdateSsoClientConfigWireRequest(
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> grants
) {}
