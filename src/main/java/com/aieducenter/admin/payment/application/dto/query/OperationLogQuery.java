package com.aieducenter.admin.payment.application.dto.query;

import java.time.LocalDateTime;

/**
 * 订单操作记录列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/operation-logs} 查询参数。字段最终以 payment 实现契约为准
 * （issue #37 / spec「payment 实现契约为准」）。</p>
 *
 * <p><b>{@code operation} 为单选</b>：payment 的 {@code OperationLogQuery.operation} 是单个
 * {@code OperationType}（EQUAL），issue #45 文案的「operation 多选」以 payment 实现契约为准收敛为单值——
 * 向单值下游转发多值会静默丢过滤条件，故 BFF 如实反映 payment 当前的过滤能力。</p>
 *
 * @since 0.1.0
 */
public record OperationLogQuery(

        /** 操作目标类型（PAYMENT / REFUND） */
        String targetType,

        /** 操作目标单号（支付订单号 / 退款订单号） */
        String targetNo,

        /** 操作类型（AUDIT_APPROVE / AUDIT_REJECT / NOTIFY_RESEND / …）——单选，对齐 payment 单值契约 */
        String operation,

        /** 操作者 ID（operator id） */
        Long operatorId,

        /** 来源系统（调用方 appName） */
        String operatorSystem,

        /** 操作结果（SUCCESS / FAILED） */
        String result,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo
) {
}
