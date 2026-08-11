package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款订单列表项响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>admin 作为 BFF 不拥有 payment 的状态语义，故状态/审核类型等以 payment 原值透传，
 * 展示文案（i18n）由前端按枚举名映射。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderSummaryResponse(

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
