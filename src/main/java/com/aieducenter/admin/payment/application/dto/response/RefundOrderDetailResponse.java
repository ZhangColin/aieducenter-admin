package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款订单详情响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.RefundOrderDetailWireResponse}
 * 映射而来，承载完整聚合全貌。
 *
 * <p>与列表项 {@link RefundOrderSummaryResponse} 区分：详情为完整聚合投影、独立演进
 * （payment 契约定型后详情可新增字段）。admin 作为 BFF 不拥有 payment 的状态语义，故状态/审核类型等
 * 以 payment 原值透传，展示文案（i18n）由前端按枚举名映射。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderDetailResponse(

        String refundOrderNo,

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        String status,

        BigDecimal refundAmount,

        String auditType,

        Long auditorId,

        String auditorName,

        LocalDateTime auditedAt,

        LocalDateTime createdAt
) {
}
