package com.aieducenter.admin.payment.application.dto.response;

import java.util.List;

/**
 * 异常监控响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.AnomaliesWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code AnomaliesResponse} 形状（ADR-0011 / issue #60）：
 * 长时滞留支付/退款单（笔数+金额嵌套）+ 近期失败（总数+按日志类型）。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；滞留阈值/失败窗口由 payment 决定。</p>
 *
 * @since 0.1.0
 */
public record AnomaliesResponse(

        StuckOrders longPendingPayments,

        StuckOrders longRefundingRefunds,

        RecentFailures recentFailures
) {

    /** 滞留单统计——笔数 + 金额（分）合计。 */
    public record StuckOrders(

            Long count,

            Long amount
    ) {
    }

    /** 近期失败统计——总次数 + 按日志类型明细。 */
    public record RecentFailures(

            Long totalCount,

            List<FailureCount> byType
    ) {
    }

    /** 日志类型失败计数——单一 logType 的失败次数。 */
    public record FailureCount(

            String logType,

            Long failureCount
    ) {
    }
}
