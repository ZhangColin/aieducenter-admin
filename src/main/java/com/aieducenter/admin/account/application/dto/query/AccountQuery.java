package com.aieducenter.admin.account.application.dto.query;

import java.time.LocalDateTime;

/**
 * 账号列表查询参数（BFF 透传 identity）。
 *
 * <p>筛选字段对齐 identity {@code GET /api/account} 查询参数（identity #70 契约未冻结，字段名为提案）：
 * email / phone / userId / status / locked（系统锁）/ 注册时间区间。</p>
 *
 * @since 0.1.0
 */
public record AccountQuery(

        /** 邮箱（精确或模糊，由 identity 决定） */
        String email,

        /** 手机号（精确或模糊，由 identity 决定） */
        String phone,

        /** 用户 ID（identity 终端用户标识） */
        String userId,

        /** 账号状态（ACTIVE / DISABLED 等 identity 账号状态枚举名，原值透传） */
        String status,

        /** 是否系统锁定（登录失败次数等触发的系统锁，区别于封号状态）；null = 不限 */
        Boolean locked,

        /** 注册时间起（含） */
        LocalDateTime registeredAtFrom,

        /** 注册时间止（含） */
        LocalDateTime registeredAtTo
) {
}
