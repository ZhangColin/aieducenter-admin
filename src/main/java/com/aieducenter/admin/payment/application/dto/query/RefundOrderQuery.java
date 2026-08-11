package com.aieducenter.admin.payment.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款订单列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/refunds} 查询参数；{@code statuses} 为多选，
 * 经 {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 展开为重复的 {@code status} 参数。
 * 字段最终以 payment 实现契约为准（issue #37）。</p>
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

        /** 退款状态多选（PENDING / REJECTED / APPROVED / REFUNDING / SUCCESS / FAILED）；空 = 不限 */
        List<String> statuses,

        /** 审核类型（AUTO 免审 / MANUAL 人工） */
        String auditType,

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
