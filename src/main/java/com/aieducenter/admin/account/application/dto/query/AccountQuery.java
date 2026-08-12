package com.aieducenter.admin.account.application.dto.query;

import java.time.LocalDateTime;

/**
 * 账号列表查询参数（BFF 透传 identity）。
 *
 * <p>筛选字段对齐 identity {@code GET /api/account} 查询参数（identity #70 已冻结，字段名/类型与
 * identity {@code AccountSearchQuery} 一致）：email / phone / userId / status / locked（系统锁）/
 * 注册时间区间（createdFrom / createdTo）。admin 作为纯透传 BFF，参数名照搬 identity，前端只用一套词表。</p>
 *
 * @since 0.1.0
 */
public record AccountQuery(

        /** 邮箱（identity INNER_LIKE 模糊匹配） */
        String email,

        /** 手机号（identity INNER_LIKE 模糊匹配） */
        String phone,

        /** 用户 ID（identity TSID；EQUAL 精确匹配，identity 实体主键 id） */
        Long userId,

        /** 账号状态（identity BaseEnum code：1=ACTIVE / 0=DISABLED；null = 不限） */
        Integer status,

        /** 是否系统锁定（登录失败累计等触发的系统锁，独立于 status）；null = 不限，传 false = 只看未锁 */
        Boolean locked,

        /** 注册时间起（含）——透传 identity {@code createdFrom}（propName=createdAt） */
        LocalDateTime createdFrom,

        /** 注册时间止（含）——透传 identity {@code createdTo}（propName=createdAt） */
        LocalDateTime createdTo
) {
}
