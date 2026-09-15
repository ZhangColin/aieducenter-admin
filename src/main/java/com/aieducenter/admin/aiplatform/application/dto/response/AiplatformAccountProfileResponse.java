package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * 账号极简档案北向响应——逐字镜像 aiplatform {@code BackofficeAccountProfileResponse}
 * （spec #62 忠实透传：不增删字段、不重命名、不换形状）。
 *
 * @param id          账号标识（aiplatform 内部 TSID 十进制字符串）
 * @param externalId  外部身份标识（OIDC sub＝identity 账户 Id）
 * @param displayName 显示名
 * @param createdAt   建档时间
 * @since 0.1.0
 */
public record AiplatformAccountProfileResponse(
        String id,
        String externalId,
        String displayName,
        LocalDateTime createdAt
) {
}
