package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/by-channel} 的 wire 镜像——按通道细分的仪表盘。
 *
 * <p>按 payMode（{@link PayModeStatWireResponse}）与 accessType（{@link AccessTypeStatWireResponse}）两个维度
 * 聚合支付笔数·金额·成功率，数据源 PaymentOrder（issue #37 二档统计）。admin 作为 BFF 纯透传——
 * <strong>不做</strong> admin 侧聚合/重算（spec「仪表盘」）。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record ChannelStatsWireResponse(

        /** 按 payMode（WECHAT/ALIPAY/UNIONPAY…）聚合的明细（payment 已排好序，admin 透传不改序） */
        List<PayModeStatWireResponse> byPayMode,

        /** 按 accessType（WEB/APP…）聚合的明细（payment 已排好序，admin 透传不改序） */
        List<AccessTypeStatWireResponse> byAccessType
) {

    /**
     * payMode 维度统计——单一支付方式的支付笔数·金额·成功率。
     *
     * @param payMode        支付方式（PaymentOrder.payMode：WECHAT|ALIPAY|UNIONPAY）
     * @param paymentCount   该支付方式支付笔数
     * @param paymentAmount  该支付方式支付总金额（元）
     * @param successRate    该支付方式支付成功率（小数，0–1 区间；最终精度以 payment 契约为准）
     */
    public record PayModeStatWireResponse(

            String payMode,

            Long paymentCount,

            BigDecimal paymentAmount,

            BigDecimal successRate
    ) {
    }

    /**
     * accessType 维度统计——单一接入类型的支付笔数·金额·成功率。
     *
     * @param accessType     接入类型（PaymentOrder.accessType：WEB|APP…）
     * @param paymentCount   该接入类型支付笔数
     * @param paymentAmount  该接入类型支付总金额（元）
     * @param successRate    该接入类型支付成功率（小数，0–1 区间；最终精度以 payment 契约为准）
     */
    public record AccessTypeStatWireResponse(

            String accessType,

            Long paymentCount,

            BigDecimal paymentAmount,

            BigDecimal successRate
    ) {
    }
}
