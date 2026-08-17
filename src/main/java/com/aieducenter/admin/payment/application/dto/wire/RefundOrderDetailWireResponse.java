package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code RefundOrder} 详情的 wire 镜像——完整聚合投影。
 *
 * <p>与列表项 {@link RefundOrderWireResponse} 同源（payment {@code RefundOrder} 聚合），
 * 但承载<strong>详情全貌</strong>投影：列表项为扫描精简、详情为完整聚合，二者独立演进
 * （payment 契约定型后，详情端可能新增列表不需要的字段）。字段以 payment 实现契约为准。</p>
 *
 * <p>枚举出口规则（ADR-0009）：{@code status}/{@code statusName}、{@code auditType}/{@code auditTypeName}，
 * 枚举 code 为 Integer。中文名由 payment 出口提供、admin 透传。</p>
 *
 * <p>金额出口规则（ADR-0011）：{@code refundAmount} 为 <strong>Long（分）</strong>，与 payment
 * {@code RefundOrderResponse.refundAmount} 同型透传、零换算。审核人出口仅 {@code auditorName}
 * ——payment 从不发送 {@code auditorId}/{@code auditedAt}（ghost，#59 删）。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderDetailWireResponse(

        String refundOrderNo,

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 退款状态（payment BaseEnum code） */
        Integer status,

        /** 退款状态中文名（payment 出口提供） */
        String statusName,

        /** 退款金额（分） */
        Long refundAmount,

        /** 审核类型（payment BaseEnum code：AUTO 免审 / MANUAL 人工） */
        Integer auditType,

        /** 审核类型中文名（payment 出口提供） */
        String auditTypeName,

        String auditorName,

        LocalDateTime createdAt
) {
}
