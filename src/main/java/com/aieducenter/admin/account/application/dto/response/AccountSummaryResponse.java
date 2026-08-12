package com.aieducenter.admin.account.application.dto.response;

/**
 * 账号列表项响应——由 {@link com.aieducenter.admin.account.application.dto.wire.AccountWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>admin 作为 BFF 不拥有 identity 的状态语义，故 {@code status}（1=ACTIVE / 0=DISABLED）以 identity
 * 原值（Integer code）透传，展示文案（i18n）由前端按 code 映射。</p>
 *
 * @since 0.1.0
 */
public record AccountSummaryResponse(

        Long userId,

        String email,

        String phone,

        String nickname,

        String avatar,

        /** 账号状态 BaseEnum code（1=ACTIVE / 0=DISABLED） */
        Integer status,

        boolean locked,

        boolean hasPassword
) {
}
