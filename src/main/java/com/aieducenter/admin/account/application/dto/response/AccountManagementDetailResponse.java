package com.aieducenter.admin.account.application.dto.response;

/**
 * 账号管理详情响应——由 {@link com.aieducenter.admin.account.application.dto.wire.AccountWireResponse}
 * 映射而来，供前端抽屉展示账号管理全貌（状态/锁定/是否有密码/资料）。
 *
 * <p>identity 的管理详情（{@code GET /api/account/{userId}/management}）与列表项返回<strong>同一个</strong>
 * {@code AccountManagementView}（字段同构），故 wire 层复用 {@code AccountWireResponse}；北向单列详情响应
 * 类型以与列表行 {@link AccountSummaryResponse} 区分语义、并为详情后续可能扩展（如状态历史）留位。
 * 当前字段与 {@code AccountSummaryResponse} 一致。</p>
 *
 * <p>admin 作为 BFF 不拥有 identity 的状态语义，{@code status}（1=ACTIVE / 0=DISABLED）以 identity
 * 原值（Integer code）透传，展示文案（i18n）由前端按 code 映射（与列表行同口径）。</p>
 *
 * @since 0.1.0
 */
public record AccountManagementDetailResponse(

        Long userId,

        String email,

        String phone,

        String nickname,

        String avatar,

        /** 账号状态 BaseEnum code（1=ACTIVE / 0=DISABLED） */
        Integer status,

        /** 是否被系统锁定（登录失败累计等，独立于 status） */
        boolean locked,

        /** 是否设过密码（社交/纯验证码账号为 false） */
        boolean hasPassword
) {
}
