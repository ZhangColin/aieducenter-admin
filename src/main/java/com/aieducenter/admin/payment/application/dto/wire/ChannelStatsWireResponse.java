package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/by-channel} 的 wire 镜像——按渠道维度细分的统计。
 *
 * <p>逐字镜像 payment 的 {@code ByChannelResponse}（ADR-0011 / issue #60）：{@code byPayMode}/{@code byAccessType}
 * 共用同一 {@link ChannelBreakdownWireResponse} 形状，渠道为枚举 {@code Integer code + channelName}（非字符串
 * token），按枚举顺序全列（缺失补零），仅统计 {@code pay_mode}/{@code access_type} 非空的支付单。聚合归
 * payment（issue #37 二档统计）；admin 纯透传——不做 admin 侧聚合/重算/重排（spec「仪表盘」）。</p>
 *
 * @param byPayMode    按支付方式（PayMode）聚合
 * @param byAccessType 按接入类型（AccessType）聚合
 * @since 0.1.0
 */
public record ChannelStatsWireResponse(

        /** 按 payMode（WECHAT/ALIPAY/UNIONPAY…）聚合的明细（payment 已排好序，admin 透传不改序） */
        List<ChannelBreakdownWireResponse> byPayMode,

        /** 按 accessType（H5/APP…）聚合的明细（payment 已排好序，admin 透传不改序） */
        List<ChannelBreakdownWireResponse> byAccessType
) {

    /**
     * 渠道维度统计——单一渠道的支付笔数·金额·成功率（payMode / accessType 两维度同构）。
     *
     * <p>枚举出口规则（ADR-0009）：{@code channelCode} 为 PayMode/AccessType 的 Integer code、配
     * {@code channelName} 显示名。</p>
     *
     * @param channelCode   渠道枚举 code（PayMode / AccessType 的 code）
     * @param channelName   渠道显示名（枚举 name）
     * @param count         总笔数
     * @param amount        总金额（分）
     * @param successCount  成功笔数（PAID）
     * @param successAmount 成功金额（分）
     * @param successRate   成功率 [0,1] 4 位小数，分母为 0 时 0（比率 BigDecimal——provider 契约）
     */
    public record ChannelBreakdownWireResponse(

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
