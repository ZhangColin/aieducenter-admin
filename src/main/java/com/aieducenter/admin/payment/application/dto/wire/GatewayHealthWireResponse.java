package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/gateway/health} 的 wire 镜像——通道健康仪表盘。
 *
 * <p>各银行接口调用次数·成功率·平均耗时·返回码分布，数据源 PaymentLog（issue #37 一档统计）。
 * admin 作为 BFF 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。
 * 最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record GatewayHealthWireResponse(

        /** 各银行接口健康度明细（payment 已排好序，admin 透传不改序） */
        List<BankInterfaceStatWireResponse> bankInterfaces
) {

    /**
     * 银行接口健康度——单一 bankInterface 的调用统计 + 返回码分布。
     *
     * @param bankInterface     银行接口（如 ICBC_PAY / WECHAT_QUERY）
     * @param callCount         调用次数
     * @param successCount      成功次数
     * @param successRate       成功率（小数，0–1 区间；最终精度以 payment 契约为准）
     * @param avgExecutionTime  平均耗时（毫秒，与 PaymentLog.executionTime 同单位）
     * @param returnCodes       返回码分布
     */
    public record BankInterfaceStatWireResponse(

            String bankInterface,

            Long callCount,

            Long successCount,

            BigDecimal successRate,

            Long avgExecutionTime,

            List<ReturnCodeStatWireResponse> returnCodes
    ) {

        /**
         * 返回码分布——单一 returnCode 的出现次数。
         *
         * @param returnCode 返回码（如 000000）
         * @param count      出现次数
         */
        public record ReturnCodeStatWireResponse(

                String returnCode,

                Long count
        ) {
        }
    }
}
