package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code RefundOrder} 列表项的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段以 payment 实现契约为准。</p>
 *
 * <p>枚举出口规则（ADR-0009）：{@code status}/{@code statusName}、{@code auditType}/{@code auditTypeName}，
 * 枚举 code 为 Integer。中文名由 payment 出口提供、admin 透传。</p>
 *
 * <p>金额出口规则（ADR-0011）：{@code refundAmount} 为 <strong>Long（分）</strong>，与 payment
 * {@code RefundOrderResponse.refundAmount} 同型透传、零换算。审核人出口仅 {@code auditorName}
 * ——payment 从不发送 {@code auditorId}/{@code auditedAt}（ghost，#59 删）；按审核人筛选走 query 侧
 * {@code auditorId}。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderWireResponse(

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
