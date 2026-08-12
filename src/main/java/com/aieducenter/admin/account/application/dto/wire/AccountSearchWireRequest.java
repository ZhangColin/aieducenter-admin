package com.aieducenter.admin.account.application.dto.wire;

import java.time.LocalDateTime;

/**
 * identity {@code GET /api/account} 列表查询的 wire 请求——BFF 出站过滤参数的载荷形状。
 *
 * <p>与 {@link com.aieducenter.admin.account.application.dto.query.AccountQuery}（北向 controller 绑定）
 * 字段同构，由 {@code AccountManagementAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.account.infrastructure.AccountClient} 只依赖 wire 层（而非 query 层），
 * 与 {@code PaymentClient} 依赖 {@code *WireRequest} 的约定一致——query DTO 是应用层内部概念，
 * 不应被基础设施 import。</p>
 *
 * <p>identity 搜索契约（identity #70）未冻结，字段名为提案，最终以 identity 实现契约为准。</p>
 *
 * @since 0.1.0
 */
public record AccountSearchWireRequest(

        String email,

        String phone,

        String userId,

        /** 账号状态枚举名（原值透传）；null = 不限 */
        String status,

        /** 是否系统锁定；null = 不限 */
        Boolean locked,

        /** 注册时间起（含） */
        LocalDateTime registeredAtFrom,

        /** 注册时间止（含） */
        LocalDateTime registeredAtTo
) {
}
