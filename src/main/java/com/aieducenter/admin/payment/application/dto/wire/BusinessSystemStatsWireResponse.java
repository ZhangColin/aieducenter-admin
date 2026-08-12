package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/by-business-system} 的 wire 镜像——按业务系统细分的仪表盘。
 *
 * <p>各业务系统的支付/退款笔数·金额·成功率·退款率（{@link BusinessSystemStatWireResponse}），
 * 数据源 PaymentOrder + RefundOrder（issue #37 二档统计）。admin 作为 BFF 纯透传——不做 admin 侧聚合/重算
 * （spec「仪表盘」）。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record BusinessSystemStatsWireResponse(

        /** 按业务系统聚合的明细（payment 已排好序，admin 透传不改序） */
        List<BusinessSystemStatWireResponse> systems
) {

    /**
     * 业务系统统计——单一业务系统的支付/退款笔数·金额·成功率·退款率。
     *
     * @param businessSystemName 业务系统名（PaymentOrder/RefundOrder.businessSystemName，即调用方 callerAppName）
     * @param paymentCount       该业务系统支付笔数
     * @param paymentAmount      该业务系统支付总金额（元）
     * @param refundCount        该业务系统退款笔数
     * @param refundAmount       该业务系统退款总金额（元）
     * @param successRate        该业务系统支付成功率（小数，0–1 区间；最终精度以 payment 契约为准）
     * @param refundRate         该业务系统退款率（小数，0–1 区间；退款笔数 / 支付笔数，最终精度以 payment 契约为准）
     */
    public record BusinessSystemStatWireResponse(

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
