package com.aieducenter.admin.account.application.dto.response;

import java.time.LocalDateTime;

/**
 * 账号列表项响应——由 {@link com.aieducenter.admin.account.application.dto.wire.AccountWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>admin 作为 BFF 不拥有 identity 的状态语义，故 status 等以 identity 原值透传，
 * 展示文案（i18n）由前端按枚举名映射。</p>
 *
 * @since 0.1.0
 */
public record AccountSummaryResponse(

        String userId,

        String email,

        String phone,

        String nickname,

        String avatar,

        String status,

        Boolean locked,

        LocalDateTime registeredAt
) {
}
