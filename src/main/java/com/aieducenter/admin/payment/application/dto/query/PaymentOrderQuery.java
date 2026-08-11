package com.aieducenter.admin.payment.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/payments} 查询参数；{@code statuses} 为多选，
 * 经 {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 展开为重复的 {@code status} 参数。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderQuery(

        /** 支付订单号 */
        String paymentOrderNo,

        /** 业务订单号 */
        String businessOrderNo,

        /** 业务系统名（精确或模糊，由 payment 决定） */
        String businessSystemName,

        /** 订单状态多选（PENDING / PAID / FAILED / CANCELLED / EXPIRED）；空 = 不限 */
        List<String> statuses,

        /** 支付方式（WECHAT / ALIPAY / UNIONPAY） */
        String payMode,

        /** 接入类型 */
        String accessType,

        /** 支付通道 */
        String paymentChannel,

        /** 金额下限（含） */
        BigDecimal amountMin,

        /** 金额上限（含） */
        BigDecimal amountMax,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo,

        /** 支付时间起（含） */
        LocalDateTime paidAtFrom,

        /** 支付时间止（含） */
        LocalDateTime paidAtTo
) {
}