package com.aieducenter.admin.account.application.dto.wire;

import java.time.LocalDateTime;

/**
 * identity 账号列表项的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 identity 内部 DTO 耦合。字段最终以 identity 实现契约为准（identity #70 未冻结）。</p>
 *
 * @since 0.1.0
 */
public record AccountWireResponse(

        /** 用户 ID（identity 终端用户标识） */
        String userId,

        String email,

        String phone,

        /** 昵称（资料） */
        String nickname,

        /** 头像 URL（资料） */
        String avatar,

        /** 账号状态（ACTIVE / DISABLED 等 identity 账号状态枚举名，原值透传） */
        String status,

        /** 是否系统锁定（登录失败次数等触发的系统锁，区别于封号状态） */
        Boolean locked,

        /** 注册时间 */
        LocalDateTime registeredAt
) {
}
