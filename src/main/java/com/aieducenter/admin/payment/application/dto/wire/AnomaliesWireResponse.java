package com.aieducenter.admin.payment.application.dto.wire;

/**
 * payment {@code GET /api/v1/stats/anomalies} 的 wire 镜像——异常监控仪表盘。
 *
 * <p>长时滞留订单（PENDING 支付单 / REFUNDING 退款单）计数 + 近期失败计数，数据源 PaymentOrder +
 * RefundOrder + PaymentLog（issue #37 二档统计）。admin 作为 BFF 纯透传——不做 admin 侧聚合/重算
 * （spec「仪表盘」）。最终字段（滞留阈值 / 失败窗口）以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record AnomaliesWireResponse(

        /** 长时滞留的 PENDING 支付单数（payment 定义的滞留阈值内仍未支付的支付单） */
        Long longPendingCount,

        /** 长时滞留的 REFUNDING 退款单数（payment 定义的滞留阈值内仍在退款中的退款单） */
        Long longRefundingCount,

        /** 近期失败计数（payment 定义的近期窗口内 PaymentLog success=false 等失败事件数） */
        Long recentFailureCount
) {
}
