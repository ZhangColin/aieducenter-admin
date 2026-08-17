package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按业务系统维度细分的统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.BusinessSystemStatsWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code ByBusinessSystemResponse} 形状（ADR-0011 / issue #60）：
 * 各业务系统的嵌套支付/退款摘要 + 退款率。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。金额 {@code Long}（分）、比率
 * {@code BigDecimal}——provider 契约的分型决定。</p>
 *
 * @since 0.1.0
 */
public record BusinessSystemStatsResponse(

        List<BusinessSystemBreakdown> businessSystems
) {

    /** 业务系统维度统计——单一业务系统的支付/退款摘要 + 退款率。 */
    public record BusinessSystemBreakdown(

            String businessSystemName,

            Summary payment,

            Summary refund,

            BigDecimal refundRate
    ) {
    }

    /** 摘要——单一业务系统侧（支付或退款）的笔数·金额（分）·成功率快照。 */
    public record Summary(

            Long count,

            Long amount,

            Long successCount,

            Long successAmount,

            BigDecimal successRate
    ) {
    }
}
