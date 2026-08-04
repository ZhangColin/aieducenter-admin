package com.aieducenter.admin.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * ApiKey 创建/轮换响应——<strong>一次性</strong>返回明文 {@code apiSecret}。
 *
 * <p>消费方必须在此次响应里捕获并妥善保管 apiSecret：之后任何接口都不可再取回明文。</p>
 *
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyCreatedResponse(
        Long id,
        Long appId,
        String apiKey,
        String apiSecret,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
