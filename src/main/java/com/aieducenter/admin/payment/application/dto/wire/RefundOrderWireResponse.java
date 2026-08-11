package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment {@code RefundOrder} 列表项的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段最终以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderWireResponse(

        String refundOrderNo,

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 退款状态（PENDING / REJECTED / APPROVED / REFUNDING / SUCCESS / FAILED） */
        String status,

        BigDecimal refundAmount,

        /** 审核类型（AUTO 免审 / MANUAL 人工） */
        String auditType,

        Long auditorId,

        String auditorName,

        LocalDateTime auditedAt,

        LocalDateTime createdAt
) {
}
