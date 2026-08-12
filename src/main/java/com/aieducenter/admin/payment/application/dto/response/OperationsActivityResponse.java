package com.aieducenter.admin.payment.application.dto.response;

import java.util.List;

/**
 * 操作员活动统计响应——由
 * {@link com.aieducenter.admin.payment.application.dto.wire.OperationsActivityWireResponse}
 * 映射而来，承载各操作员的操作类型·笔数明细 + 通知重发次数。
 *
 * <p>聚合/重算归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OperationsActivityResponse(

        List<OperatorActivityStat> operators
) {

    /** 操作员活动统计——单一操作员的操作类型·笔数分布 + 通知重发次数。 */
    public record OperatorActivityStat(

            Long operatorId,

            String operatorName,

            List<OperationCount> operations,

            Long notificationResendCount
    ) {
    }

    /** 操作类型计数——单一操作类型的笔数。 */
    public record OperationCount(

            String operation,

            Long count
    ) {
    }
}
