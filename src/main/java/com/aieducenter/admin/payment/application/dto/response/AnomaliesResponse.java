package com.aieducenter.admin.payment.application.dto.response;

/**
 * 异常监控统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.AnomaliesWireResponse}
 * 映射而来，承载长时滞留订单计数 + 近期失败计数。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）。最终字段（滞留阈值 / 失败窗口）以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record AnomaliesResponse(

        Long longPendingCount,

        Long longRefundingCount,

        Long recentFailureCount
) {
}
