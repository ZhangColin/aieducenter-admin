package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付总览响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.PaymentOverviewWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code PaymentOverviewResponse} 形状（ADR-0011 / issue #60）：
 * 支付/退款嵌套摘要 + 净额（分）+ 趋势分桶。
 *
 * <p>聚合/补零归 payment（spec「仪表盘」）；admin 透传不改序。金额一律 {@code Long}（分）、比率
 * {@code BigDecimal}——provider 契约的分型决定。</p>
 *
 * @since 0.1.0
 */
public record PaymentOverviewResponse(

        Summary payment,

        Summary refund,

        Long netAmount,

        List<TrendBucket> trend
) {

    /** 摘要——窗口内的笔数·金额（分）·成功笔数·成功金额·成功率快照。 */
    public record Summary(

            Long count,

            Long amount,

            Long successCount,

            Long successAmount,

            BigDecimal successRate
    ) {
    }

    /** 趋势分桶——某时间窗口内的支付/退款笔数·金额快照（含成功子集）。 */
    public record TrendBucket(

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
