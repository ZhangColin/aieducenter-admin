package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/gateway/health} 的 wire 镜像——银行网关健康度。
 *
 * <p>逐字镜像 payment 的 {@code GatewayHealthResponse}（ADR-0011 / issue #60）：列表名为 {@code interfaces}，
 * 每项含 {@code bankCode}/{@code bankInterface}；次数为 {@code Long}、成功率/平均耗时为 {@code BigDecimal}
 * （均值单位毫秒、字段名 {@code avgExecutionTimeMs}）。数据源 PaymentLog（issue #37 一档统计）；聚合归
 * payment，admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param interfaces 各银行接口健康度明细（payment 已排好序，admin 透传不改序）
 * @since 0.1.0
 */
public record GatewayHealthWireResponse(

        /** 各银行接口健康度明细 */
        List<InterfaceHealthWireResponse> interfaces
) {

    /**
     * 银行接口健康度——单一 bankInterface 的调用统计 + 返回码分布。
     *
     * @param bankCode           银行编码（如 ICBC / WECHAT）
     * @param bankInterface      银行接口名（如 ICBC_PAY / WECHAT_QUERY）
     * @param totalCount         调用次数
     * @param successCount       成功次数
     * @param successRate        成功率 [0,1] 4 位小数（比率 BigDecimal——provider 契约）
     * @param avgExecutionTimeMs 平均耗时毫秒（2 位小数，BigDecimal——provider 契约）
     * @param returnCodes        返回码分布
     */
    public record InterfaceHealthWireResponse(

            String bankCode,

            String bankInterface,

            Long totalCount,

            Long successCount,

            BigDecimal successRate,

            BigDecimal avgExecutionTimeMs,

            List<ReturnCodeCountWireResponse> returnCodes
    ) {

        /**
         * 返回码分布——单一 returnCode 的出现次数。
         *
         * @param returnCode 返回码（如 000000）
         * @param count      出现次数
         */
        public record ReturnCodeCountWireResponse(

                String returnCode,

                Long count
        ) {
        }
    }
}
