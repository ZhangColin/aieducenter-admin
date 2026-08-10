package com.aieducenter.admin.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SsoClient 凭证（开通/重置 {@code client_secret}）响应——<strong>一次性</strong>返回明文 {@code clientSecret}。
 *
 * <p>{@code client_id} 终身稳定，重置只换 {@code client_secret}。消费方必须在此次响应里捕获并妥善保管 clientSecret：
 * 之后任何接口都不可再取回明文。</p>
 *
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SsoClientCreatedResponse(
        Long id,
        Long appId,
        String clientId,
        String clientSecret,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> grants,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
