package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按渠道维度细分的统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.ChannelStatsWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code ByChannelResponse} 形状（ADR-0011 / issue #60）：
 * payMode / accessType 两维度共用同一 {@link ChannelBreakdown}。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。金额 {@code Long}（分）、比率
 * {@code BigDecimal}——provider 契约的分型决定。</p>
 *
 * @since 0.1.0
 */
public record ChannelStatsResponse(

        List<ChannelBreakdown> byPayMode,

        List<ChannelBreakdown> byAccessType
) {

    /**
     * 渠道维度统计——单一渠道的支付笔数·金额·成功率。
     *
     * <p>枚举出口规则（ADR-0009）：{@code channelCode} 为 PayMode/AccessType 的 Integer code、配
     * {@code channelName} 显示名。</p>
     */
    public record ChannelBreakdown(

            Integer channelCode,

            String channelName,

            Long count,

            Long amount,

            Long successCount,

            Long successAmount,

            BigDecimal successRate
    ) {
    }
}
