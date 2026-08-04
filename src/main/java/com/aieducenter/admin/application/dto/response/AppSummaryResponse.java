package com.aieducenter.admin.application.dto.response;

import java.time.LocalDateTime;

/**
 * 应用列表项响应——不含 apiSecret / clientSecret。
 *
 * @since 0.1.0
 */
public record AppSummaryResponse(
        Long id,
        String appCode,
        String name,
        String description,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
