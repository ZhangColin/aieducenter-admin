package com.aieducenter.admin.account.application.dto.wire;

import java.time.LocalDateTime;

/**
 * identity {@code GET /api/account} 列表查询的 wire 请求——BFF 出站过滤参数的载荷形状。
 *
 * <p>与 {@link com.aieducenter.admin.account.application.dto.query.AccountQuery}（北向 controller 绑定）
 * 字段同构（identity #70 已冻结，字段名/类型对齐 identity {@code AccountSearchQuery}），由
 * {@code AccountManagementAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.account.infrastructure.AccountClient} 只依赖 wire 层（而非 query 层），
 * 与 {@code PaymentClient} 依赖 {@code *WireRequest} 的约定一致——query DTO 是应用层内部概念，
 * 不应被基础设施 import。</p>
 *
 * @since 0.1.0
 */
public record AccountSearchWireRequest(

        String email,

        String phone,

        /** 用户 ID（identity TSID） */
        Long userId,

        /** 账号状态 BaseEnum code（1=ACTIVE / 0=DISABLED）；null = 不限 */
        Integer status,

        /** 是否系统锁定；null = 不限 */
        Boolean locked,

        /** 注册时间起（含）——对齐 identity {@code createdFrom} */
        LocalDateTime createdFrom,

        /** 注册时间止（含）——对齐 identity {@code createdTo} */
        LocalDateTime createdTo
) {
}
