package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付总览响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.PaymentOverviewWireResponse}
 * 映射而来，承载支付/退款笔数·金额·成功率·净额顶层快照 + 按时间分桶趋势。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record PaymentOverviewResponse(

        Long paymentCount,

        BigDecimal paymentAmount,

        Long refundCount,

        BigDecimal refundAmount,

        BigDecimal successRate,

        BigDecimal netAmount,

        List<TrendBucket> trend
) {

    /** 趋势分桶——某时间窗口内的支付/退款笔数·金额快照。 */
    public record TrendBucket(

            LocalDateTime bucket,

            Long paymentCount,

            BigDecimal paymentAmount,

            Long refundCount,

            BigDecimal refundAmount
    ) {
    }
}
