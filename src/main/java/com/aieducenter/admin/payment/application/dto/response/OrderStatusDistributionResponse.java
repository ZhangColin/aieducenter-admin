package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单状态分布响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OrderStatusDistributionWireResponse}
 * 映射而来，承载支付/退款各状态在途笔数·金额 + 退款待审核积压。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OrderStatusDistributionResponse(

        List<StatusBucket> paymentStatuses,

        List<StatusBucket> refundStatuses,

        Long refundPendingAuditCount
) {

    /**
     * 状态分桶——某状态下的在途笔数·金额。
     *
     * <p>{@code amount} 语义随所属列表而定：在 {@code paymentStatuses} 中为支付金额，
     * 在 {@code refundStatuses} 中为退款金额。</p>
     */
    public record StatusBucket(

            String status,

            Long count,

            BigDecimal amount
    ) {
    }
}
