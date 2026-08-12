package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通道健康响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.GatewayHealthWireResponse}
 * 映射而来，承载各银行接口调用次数·成功率·平均耗时·返回码分布。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record GatewayHealthResponse(

        List<BankInterfaceStat> bankInterfaces
) {

    /** 银行接口健康度——单一 bankInterface 的调用统计 + 返回码分布。 */
    public record BankInterfaceStat(

            String bankInterface,

            Long callCount,

            Long successCount,

            BigDecimal successRate,

            Long avgExecutionTime,

            List<ReturnCodeStat> returnCodes
    ) {

        /** 返回码分布——单一 returnCode 的出现次数。 */
        public record ReturnCodeStat(

                String returnCode,

                Long count
        ) {
        }
    }
}
