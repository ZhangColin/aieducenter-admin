package com.aieducenter.admin.account.application.dto.wire;

/**
 * identity 账号列表项的 wire 镜像——与 identity {@code AccountManagementView}（#67/#70）字段同构。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，不与 identity 内部
 * DTO 耦合。{@code status} 经 cartisan-web {@code BaseEnumSerializer} 序列化为 Integer code
 *（1=ACTIVE / 0=DISABLED），此处按 Integer 反序列化原值透传。{@code locked} / {@code hasPassword}
 * 在 identity 为原始 boolean（恒在）。</p>
 *
 * @since 0.1.0
 */
public record AccountWireResponse(

        /** 用户 ID（identity TSID） */
        Long userId,

        String email,

        String phone,

        /** 昵称（取自 Profile，可空） */
        String nickname,

        /** 头像 URL（取自 Profile，可空） */
        String avatar,

        /** 账号状态 BaseEnum code（1=ACTIVE / 0=DISABLED） */
        Integer status,

        /** 是否被系统锁定（登录失败累计等，独立于 status） */
        boolean locked,

        /** 是否设过密码（社交/纯验证码账号为 false） */
        boolean hasPassword
) {
}
