package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按业务系统细分的统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.BusinessSystemStatsWireResponse}
 * 映射而来，承载各业务系统的支付/退款笔数·金额·成功率·退款率。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record BusinessSystemStatsResponse(

        List<BusinessSystemStat> systems
) {

    /** 业务系统统计——单一业务系统的支付/退款笔数·金额·成功率·退款率。 */
    public record BusinessSystemStat(

            String businessSystemName,

            Long paymentCount,

            BigDecimal paymentAmount,

            Long refundCount,

            BigDecimal refundAmount,

            BigDecimal successRate,

            BigDecimal refundRate
    ) {
    }
}
