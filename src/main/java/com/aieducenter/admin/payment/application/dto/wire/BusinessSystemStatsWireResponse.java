package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/by-business-system} 的 wire 镜像——按业务系统维度细分的统计。
 *
 * <p>逐字镜像 payment 的 {@code ByBusinessSystemResponse}（ADR-0011 / issue #60）：列表名为
 * {@code businessSystems}，每系统的支付/退款为<strong>嵌套</strong> {@link SummaryWireResponse}（非扁平字段）。
 * payment / refund 双源按 {@code businessSystemName} 并集（保序：payment 顺序优先，refund-only 追加在后），
 * 缺失侧补零。聚合归 payment（issue #37 二档统计）；admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param businessSystems 各业务系统明细（按 payment rollup 顺序 + refund-only 追加）
 * @since 0.1.0
 */
public record BusinessSystemStatsWireResponse(

        /** 按业务系统聚合的明细（payment 已排好序，admin 透传不改序） */
        List<BusinessSystemBreakdownWireResponse> businessSystems
) {

    /**
     * 业务系统维度统计——单一业务系统的支付/退款摘要 + 退款率。
     *
     * @param businessSystemName 业务系统名（PaymentOrder/RefundOrder.businessSystemName，即调用方 callerAppName）
     * @param payment            支付摘要
     * @param refund             退款摘要
     * @param refundRate         退款率 = 退款成功笔数 / 支付成功笔数，[0,1] 4 位小数（分母 0 时 0.0000）
     */
    public record BusinessSystemBreakdownWireResponse(

            String businessSystemName,

            SummaryWireResponse payment,

            SummaryWireResponse refund,

            BigDecimal refundRate
    ) {
    }

    /**
     * 摘要——单一业务系统侧（支付或退款）的笔数·金额·成功率快照。
     *
     * @param count         总笔数
     * @param amount        总金额（分）
     * @param successCount  成功笔数（PAID / SUCCESS）
     * @param successAmount 成功金额（分）
     * @param successRate   成功率 [0,1] 4 位小数，分母为 0 时 0（比率 BigDecimal——provider 契约）
     */
    public record SummaryWireResponse(

            Long count,

            Long amount,

            Long successCount,

            Long successAmount,

            BigDecimal successRate
    ) {
    }
}
