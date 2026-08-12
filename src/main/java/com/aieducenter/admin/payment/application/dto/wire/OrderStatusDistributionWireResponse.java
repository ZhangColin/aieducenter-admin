package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/orders/status-distribution} 的 wire 镜像——订单状态分布仪表盘。
 *
 * <p>各状态在途笔数·金额（支付单 + 退款单两列分布）+ 退款待审核积压，数据源 PaymentOrder + RefundOrder
 * （issue #37 一档统计）。admin 作为 BFF 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。
 * 最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OrderStatusDistributionWireResponse(

        /** 支付单各状态分布（status ∈ PENDING/PAID/FAILED/CANCELLED/EXPIRED） */
        List<StatusBucketWireResponse> paymentStatuses,

        /** 退款单各状态分布（status ∈ PENDING/REJECTED/APPROVED/REFUNDING/SUCCESS/FAILED） */
        List<StatusBucketWireResponse> refundStatuses,

        /** 退款待审核积压笔数（status=PENDING 的退款单数；运营关注积压，单独 roll-up） */
        Long refundPendingAuditCount
) {

    /**
     * 状态分桶——某状态下的在途笔数·金额。
     *
     * <p>{@code amount} 语义随所属列表而定：在 {@code paymentStatuses} 中为支付金额，
     * 在 {@code refundStatuses} 中为退款金额。</p>
     *
     * @param status 状态名
     * @param count  该状态笔数
     * @param amount 该状态金额（元；支付/退款随上下文）
     */
    public record StatusBucketWireResponse(

            String status,

            Long count,

            BigDecimal amount
    ) {
    }
}
