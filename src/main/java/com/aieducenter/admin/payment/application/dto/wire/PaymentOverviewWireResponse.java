package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/payments/overview} 的 wire 镜像——支付总览仪表盘。
 *
 * <p>覆盖支付/退款笔数·金额·成功率·净额（顶层快照）+ 按时间分桶趋势（{@link TrendBucketWireResponse}），
 * 数据源 PaymentOrder + RefundOrder（issue #37 一档统计）。admin 作为 BFF 纯透传——
 * <strong>不做</strong> admin 侧聚合/重算（spec「仪表盘」）。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record PaymentOverviewWireResponse(

        /** 支付订单笔数 */
        Long paymentCount,

        /** 支付订单总金额（元） */
        BigDecimal paymentAmount,

        /** 退款订单笔数 */
        Long refundCount,

        /** 退款订单总金额（元） */
        BigDecimal refundAmount,

        /** 支付成功率（小数，0–1 区间；最终精度以 payment 契约为准） */
        BigDecimal successRate,

        /** 净额 = 支付金额 − 退款金额（元） */
        BigDecimal netAmount,

        /** 按时间分桶的趋势序列（payment 已排好序，admin 透传不改序） */
        List<TrendBucketWireResponse> trend
) {

    /**
     * 趋势分桶——某时间窗口内的支付/退款笔数·金额快照，用于绘制总览趋势图。
     *
     * @param bucket         分桶起点时间（payment 决定粒度：日/小时…）
     * @param paymentCount   该桶内支付笔数
     * @param paymentAmount  该桶内支付金额（元）
     * @param refundCount    该桶内退款笔数
     * @param refundAmount   该桶内退款金额（元）
     */
    public record TrendBucketWireResponse(

            LocalDateTime bucket,

            Long paymentCount,

            BigDecimal paymentAmount,

            Long refundCount,

            BigDecimal refundAmount
    ) {
    }
}
