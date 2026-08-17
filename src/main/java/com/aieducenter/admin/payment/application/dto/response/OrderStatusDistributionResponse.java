package com.aieducenter.admin.payment.application.dto.response;

import java.util.List;

/**
 * 订单状态分布响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OrderStatusDistributionWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code StatusDistributionResponse} 形状（ADR-0011 / issue #60）：
 * 支付/退款各状态分桶 + 嵌套退款待审核积压（笔数·金额）。
 *
 * <p>聚合归 payment；admin 透传不改序。分桶 {@code count}/{@code amount} 均为 {@code Long}（金额为分）。</p>
 *
 * @since 0.1.0
 */
public record OrderStatusDistributionResponse(

        List<PaymentStatusBucket> paymentStatuses,

        List<RefundStatusBucket> refundStatuses,

        Backlog refundBacklog
) {

    /**
     * 支付状态分桶——某状态下的在途笔数·金额。
     *
     * <p>枚举出口规则（ADR-0009）：{@code status} 为 Integer code、配 {@code statusName} 中文名；前端直读
     * {@code statusName} 显示。{@code amount} 为支付金额（分）。</p>
     */
    public record PaymentStatusBucket(

            Integer status,

            String statusName,

            Long count,

            Long amount
    ) {
    }

    /** 退款状态分桶——某状态下的在途笔数·金额（形状与支付分桶同构，payment 源码即两个独立 record）。 */
    public record RefundStatusBucket(

            Integer status,

            String statusName,

            Long count,

            Long amount
    ) {
    }

    /** 退款待审核积压——PENDING 退款单的笔数与金额（分）；积压金额 {@code pendingAmount} 首次透出（#60）。 */
    public record Backlog(

            Long pendingCount,

            Long pendingAmount
    ) {
    }
}
