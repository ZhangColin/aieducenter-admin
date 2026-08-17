package com.aieducenter.admin.payment.application.dto.wire;

import java.util.List;

/**
 * payment {@code GET /api/v1/stats/anomalies} 的 wire 镜像——异常监控。
 *
 * <p>逐字镜像 payment 的 {@code AnomaliesResponse}（ADR-0011 / issue #60）：三类异常均为<strong>嵌套</strong>
 * 形状——「长时」滞留单（{@link StuckOrdersWireResponse}，阈值 {@code payment.stats.anomaly.long-*-hours} 可配，
 * 笔数+金额）×2 + 「近期」查询/回调失败（{@link RecentFailuresWireResponse}，窗口
 * {@code payment.stats.anomaly.failure-window-hours} 可配，总数+按日志类型明细）。数据源 PaymentOrder +
 * RefundOrder + PaymentLog（issue #37 二档统计）；admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param longPendingPayments  长时 PENDING 支付单（超 long-pending-payment-hours 仍未终态）
 * @param longRefundingRefunds 长时 REFUNDING 退款单（超 long-refunding-refund-hours 仍未终态）
 * @param recentFailures       近期查询/回调失败（窗口内 success=FALSE 的网关日志）
 * @since 0.1.0
 */
public record AnomaliesWireResponse(

        /** 长时滞留的 PENDING 支付单——笔数 + 金额（分） */
        StuckOrdersWireResponse longPendingPayments,

        /** 长时滞留的 REFUNDING 退款单——笔数 + 金额（分） */
        StuckOrdersWireResponse longRefundingRefunds,

        /** 近期失败——总次数 + 按日志类型明细 */
        RecentFailuresWireResponse recentFailures
) {

    /**
     * 滞留单统计——笔数 + 金额（分）合计。
     *
     * @param count  滞留笔数
     * @param amount 滞留金额合计（分）
     */
    public record StuckOrdersWireResponse(

            Long count,

            Long amount
    ) {
    }

    /**
     * 近期失败统计——总次数 + 按日志类型明细。
     *
     * @param totalCount 失败总次数（各类型求和）
     * @param byType     按日志类型明细
     */
    public record RecentFailuresWireResponse(

            Long totalCount,

            List<FailureCountWireResponse> byType
    ) {
    }

    /**
     * 日志类型失败计数——单一 logType 的失败次数。
     *
     * @param logType      日志类型（PAYMENT_QUERY / REFUND_QUERY / PAYMENT_CALLBACK）
     * @param failureCount 该类型失败次数
     */
    public record FailureCountWireResponse(

            String logType,

            Long failureCount
    ) {
    }
}
