package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通道健康响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.GatewayHealthWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code GatewayHealthResponse} 形状（ADR-0011 / issue #60）：
 * 各银行接口（含 bankCode）调用次数·成功率·平均耗时（毫秒，BigDecimal）·返回码分布。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序。比率/均值为 {@code BigDecimal}——provider 契约。</p>
 *
 * @since 0.1.0
 */
public record GatewayHealthResponse(

        List<InterfaceHealth> interfaces
) {

    /** 银行接口健康度——单一 bankInterface 的调用统计 + 返回码分布。 */
    public record InterfaceHealth(

            String bankCode,

            String bankInterface,

            Long totalCount,

            Long successCount,

            BigDecimal successRate,

            BigDecimal avgExecutionTimeMs,

            List<ReturnCodeCount> returnCodes
    ) {

        /** 返回码分布——单一 returnCode 的出现次数。 */
        public record ReturnCodeCount(

                String returnCode,

                Long count
        ) {
        }
    }
}
