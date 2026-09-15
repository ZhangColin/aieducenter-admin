package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 账号极简档案的 wire 镜像——与 aiplatform {@code BackofficeAccountProfileResponse}
 * （#154 已冻结）字段同构。用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient}
 * 响应，不与 aiplatform 内部 DTO 耦合。
 *
 * <p>注意 {@code id} 为 <strong>String</strong>（TSID 十进制串——aiplatform 的 REST Long 序列化
 * 口径，区别于 identity {@code AccountManagementView.userId} 的 Long），原值透传。</p>
 *
 * @since 0.1.0
 */
public record AiplatformAccountProfileWireResponse(

        /** 账号标识（aiplatform 内部 TSID 十进制字符串） */
        String id,

        /** 外部身份标识（OIDC sub＝identity 账户 Id，对外正身） */
        String externalId,

        /** 显示名 */
        String displayName,

        /** 建档时间 */
        LocalDateTime createdAt
) {
}
