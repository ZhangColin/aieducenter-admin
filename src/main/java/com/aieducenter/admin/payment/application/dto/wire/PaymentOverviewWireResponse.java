package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/payments/overview} 的 wire 镜像——支付交易概览。
 *
 * <p>逐字镜像 payment 的 {@code PaymentOverviewResponse}（ADR-0011 / issue #60）：{@code payment}/{@code refund}
 * 为<strong>嵌套</strong>摘要（非扁平字段），金额一律 {@code Long}（分）、比率 {@code BigDecimal}——金额与比率
 * 分型是 provider 契约。{@code trend} 为按 granularity 补零的连续序列（payment 已排好序，admin 透传不改序）。
 * 聚合/补零归 payment（issue #37 一档统计）；admin 作为 BFF 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param payment   支付摘要
 * @param refund    退款摘要
 * @param netAmount 净额 = 支付成功金额 − 退款成功金额（分）
 * @param trend     趋势分桶序列（按日/小时补零，payment 已排好序）
 * @since 0.1.0
 */
public record PaymentOverviewWireResponse(

        /** 支付摘要（窗口内笔数·金额·成功笔数·成功金额·成功率） */
        SummaryWireResponse payment,

        /** 退款摘要（窗口内笔数·金额·成功笔数·成功金额·成功率） */
        SummaryWireResponse refund,

        /** 净额 = 支付成功金额 − 退款成功金额（分） */
        Long netAmount,

        /** 趋势分桶序列（payment 已排好序，admin 透传不改序） */
        List<TrendBucketWireResponse> trend
) {

    /**
     * 摘要——窗口内的笔数·金额·成功率快照。
     *
     * @param count         总笔数（窗口内）
     * @param amount        总金额（分，SUM(amount) / SUM(refund_amount)）
     * @param successCount  成功笔数（PAID / SUCCESS）
     * @param successAmount 成功金额（分）
     * @param successRate   成功率 [0,1] 4 位小数，分母为 0 时 0（比率 BigDecimal——provider 契约）
     */
    public record SummaryWireResponse(

            Long count,

            Long amount,

            Long successCount,

            Long successAmount,

            BigDecimal successRate
    ) {
    }

    /**
     * 趋势分桶——某时间窗口内的支付/退款笔数·金额快照（含成功子集）。
     *
     * @param bucket         桶起点（payment 决定粒度：日/小时…）
     * @param paymentCount   该桶内支付笔数
     * @param paymentAmount  该桶内支付金额（分）
     * @param paidCount      该桶内支付成功笔数
     * @param paidAmount     该桶内支付成功金额（分）
     * @param refundCount    该桶内退款笔数
     * @param refundAmount   该桶内退款金额（分）
     * @param refundedCount  该桶内退款成功笔数
     * @param refundedAmount 该桶内退款成功金额（分）
     */
    public record TrendBucketWireResponse(

            LocalDateTime bucket,

            Long paymentCount,

            Long paymentAmount,

            Long paidCount,

            Long paidAmount,

            Long refundCount,

            Long refundAmount,

            Long refundedCount,

            Long refundedAmount
    ) {
    }
}
