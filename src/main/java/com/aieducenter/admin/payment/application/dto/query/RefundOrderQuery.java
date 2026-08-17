package com.aieducenter.admin.payment.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款订单列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/refunds} 查询参数；{@code statuses} 为多选，
 * 经 {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 展开为重复的 {@code status} 参数。
 * 字段以 payment 实现契约为准。</p>
 *
 * <p>枚举筛选项（{@code statuses} / {@code auditType}）以 payment BaseEnum 的 Integer code 传递（payment 按 code 绑定枚举）。
 * 退款金额筛选 {@code refundAmountMin/Max} 为 <strong>Long（分）</strong>，与 payment {@code RefundOrderQuery}
 * 同型透传、零换算（ADR-0011——前端已按分提交，admin 不做分↔元换算）。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderQuery(

        /** 退款订单号 */
        String refundOrderNo,

        /** 支付订单号 */
        String paymentOrderNo,

        /** 业务订单号 */
        String businessOrderNo,

        /** 业务系统名（精确或模糊，由 payment 决定） */
        String businessSystemName,

        /** 退款状态多选（payment BaseEnum code）；空 = 不限 */
        List<Integer> statuses,

        /** 审核类型（payment BaseEnum code：AUTO 免审 / MANUAL 人工） */
        Integer auditType,

        /** 审核人 ID（operator id；payment {@code RefundOrderQuery} 支持按审核人筛选） */
        Long auditorId,

        /** 退款金额下限（分，含） */
        Long refundAmountMin,

        /** 退款金额上限（分，含） */
        Long refundAmountMax,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo
) {
}
