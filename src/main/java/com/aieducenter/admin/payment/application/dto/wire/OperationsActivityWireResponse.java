package com.aieducenter.admin.payment.application.dto.wire;

import java.util.List;

/**
 * payment {@code GET /api/v1/stats/operations/activity} 的 wire 镜像——操作活跃度。
 *
 * <p>逐字镜像 payment 的 {@code OperationsActivityResponse}（ADR-0011 / issue #60）：{@code byOperator}（各操作员
 * 操作类型/笔数分布 + 该员全部操作总数）+ {@code notifyResend} 通知重发汇总（总数 + 按来源业务系统）。
 * {@code operatorId}/{@code businessSystem} 为 null 的系统动作单列一组（保留 null）。数据源 OperationLog
 * （单一，issue #37 二档统计）；admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param byOperator   各操作员操作类型/笔数分布（payment 已排好序，admin 透传不改序）
 * @param notifyResend 通知重发汇总（总数 + 按来源业务系统）
 * @since 0.1.0
 */
public record OperationsActivityWireResponse(

        /** 按操作员聚合的活动明细 */
        List<OperatorActivityWireResponse> byOperator,

        /** 通知重发汇总（NOTIFY_RESEND 操作的 roll-up） */
        NotifyResendActivityWireResponse notifyResend
) {

    /**
     * 操作员活动统计——单一操作员的操作类型·笔数分布 + 全部操作总数。
     *
     * @param operatorId   操作者 id（可空——系统动作单列一组）
     * @param operatorName 操作者名（可空）
     * @param totalCount   该操作员全部操作笔数
     * @param operations   该操作员各操作类型明细
     */
    public record OperatorActivityWireResponse(

            Long operatorId,

            String operatorName,

            Long totalCount,

            List<OperationCountWireResponse> operations
    ) {
    }

    /**
     * 操作类型计数——单一操作类型的笔数。
     *
     * <p>枚举出口规则（ADR-0009）：{@code operation} 为 OperationType 的 Integer code、配 {@code operationName}
     * 显示名。</p>
     *
     * @param operation     OperationType 的 Integer code
     * @param operationName 操作类型显示名（OperationType.getName()）
     * @param count         该操作类型笔数
     */
    public record OperationCountWireResponse(

            Integer operation,

            String operationName,

            Long count
    ) {
    }

    /**
     * 通知重发汇总——总次数 + 按来源业务系统归组。
     *
     * @param totalCount       通知重发总次数
     * @param byBusinessSystem 按来源业务系统（operator_system）归组
     */
    public record NotifyResendActivityWireResponse(

            Long totalCount,

            List<SystemResendCountWireResponse> byBusinessSystem
    ) {
    }

    /**
     * 来源系统重发计数——单一来源业务系统的通知重发次数。
     *
     * @param businessSystem 来源业务系统（可空——系统动作单列一组）
     * @param count          该来源系统的通知重发次数
     */
    public record SystemResendCountWireResponse(

            String businessSystem,

            Long count
    ) {
    }
}
