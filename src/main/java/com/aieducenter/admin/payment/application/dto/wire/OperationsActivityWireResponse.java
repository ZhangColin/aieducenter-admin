package com.aieducenter.admin.payment.application.dto.wire;

import java.util.List;

/**
 * payment {@code GET /api/v1/stats/operations/activity} 的 wire 镜像——操作员活动仪表盘。
 *
 * <p>各操作员的操作类型·笔数明细（{@link OperatorActivityStatWireResponse}）+ 通知重发次数 roll-up，
 * 数据源 OperationLog（issue #37 二档统计）。admin 作为 BFF 纯透传——不做 admin 侧聚合/重算
 * （spec「仪表盘」）。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OperationsActivityWireResponse(

        /** 按操作员聚合的活动明细（payment 已排好序，admin 透传不改序） */
        List<OperatorActivityStatWireResponse> operators
) {

    /**
     * 操作员活动统计——单一操作员的操作类型·笔数分布 + 通知重发次数。
     *
     * @param operatorId             操作员 ID（OperationLog.operatorId）
     * @param operatorName           操作员姓名（OperationLog.operatorName）
     * @param operations             该操作员各操作类型的笔数明细（payment 已排好序，admin 透传不改序）
     * @param notificationResendCount 该操作员通知重发总次数（NOTIFY_RESEND 操作的 roll-up）
     */
    public record OperatorActivityStatWireResponse(

            Long operatorId,

            String operatorName,

            List<OperationCountWireResponse> operations,

            Long notificationResendCount
    ) {
    }

    /**
     * 操作类型计数——单一操作类型（AUDIT_APPROVE/AUDIT_REJECT/NOTIFY_RESEND…）的笔数。
     *
     * @param operation 操作类型（OperationLog.operation）
     * @param count     该操作类型笔数
     */
    public record OperationCountWireResponse(

            String operation,

            Long count
    ) {
    }
}
