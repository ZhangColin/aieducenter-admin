package com.aieducenter.admin.payment.application.dto.query;

import java.math.BigDecimal;
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
 * 退款金额筛选 {@code refundAmountMin/Max} 暂以元（BigDecimal）承，与 payment 的分（Long）对齐见 #55。</p>
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

        /** 审核人 ID（operator id） */
        Long auditorId,

        /** 退款金额下限（含） */
        BigDecimal refundAmountMin,

        /** 退款金额上限（含） */
        BigDecimal refundAmountMax,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo
) {
}
