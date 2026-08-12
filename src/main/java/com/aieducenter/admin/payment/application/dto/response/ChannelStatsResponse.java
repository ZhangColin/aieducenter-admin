package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按通道细分的统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.ChannelStatsWireResponse}
 * 映射而来，承载按 payMode / accessType 两个维度聚合的支付笔数·金额·成功率。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record ChannelStatsResponse(

        List<PayModeStat> byPayMode,

        List<AccessTypeStat> byAccessType
) {

    /** payMode 维度统计——单一支付方式的支付笔数·金额·成功率。 */
    public record PayModeStat(

            String payMode,

            Long paymentCount,

            BigDecimal paymentAmount,

            BigDecimal successRate
    ) {
    }

    /** accessType 维度统计——单一接入类型的支付笔数·金额·成功率。 */
    public record AccessTypeStat(

            String accessType,

            Long paymentCount,

            BigDecimal paymentAmount,

            BigDecimal successRate
    ) {
    }
}
