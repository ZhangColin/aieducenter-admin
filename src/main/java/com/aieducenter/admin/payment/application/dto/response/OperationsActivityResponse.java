package com.aieducenter.admin.payment.application.dto.response;

import java.util.List;

/**
 * 操作员活动统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.OperationsActivityWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code OperationsActivityResponse} 形状（ADR-0011 / issue #60）：
 * 各操作员操作类型·笔数明细（枚举 Integer code + 中文名）+ 通知重发汇总（总数 + 按来源业务系统）。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序、不重算 roll-up。</p>
 *
 * @since 0.1.0
 */
public record OperationsActivityResponse(

        List<OperatorActivity> byOperator,

        NotifyResendActivity notifyResend
) {

    /** 操作员活动统计——单一操作员的操作类型·笔数分布 + 全部操作总数。 */
    public record OperatorActivity(

            Long operatorId,

            String operatorName,

            Long totalCount,

            List<OperationCount> operations
    ) {
    }

    /** 操作类型计数——单一操作类型的笔数（枚举 Integer code + operationName 中文名，ADR-0009）。 */
    public record OperationCount(

            Integer operation,

            String operationName,

            Long count
    ) {
    }

    /** 通知重发汇总——总次数 + 按来源业务系统归组（来源可空——系统动作单列一组）。 */
    public record NotifyResendActivity(

            Long totalCount,

            List<SystemResendCount> byBusinessSystem
    ) {
    }

    /** 来源系统重发计数——单一来源业务系统的通知重发次数。 */
    public record SystemResendCount(

            String businessSystem,

            Long count
    ) {
    }
}
